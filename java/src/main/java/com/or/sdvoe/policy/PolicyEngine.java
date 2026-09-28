package com.or.sdvoe.policy;

import com.or.sdvoe.domain.DistanceClass;
import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.LockMode;
import com.or.sdvoe.domain.PolicyConstraints;
import com.or.sdvoe.domain.PolicyDecision;
import com.or.sdvoe.domain.SinkRole;
import com.or.sdvoe.domain.SourceType;
import com.or.sdvoe.domain.VideoSink;
import com.or.sdvoe.domain.VideoSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads YAML strategies and resolves Matrix vs SDVoE decisions.
 */
public class PolicyEngine {

    private final Map<String, Object> policies;

    @SuppressWarnings("unchecked")
    public PolicyEngine(Path configPath) {
        try (InputStream in = Files.newInputStream(configPath)) {
            Map<String, Object> cfg = new Yaml().load(in);
            this.policies = (Map<String, Object>) cfg.getOrDefault("source_type_policies", Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load policy config: " + configPath, e);
        }
    }

    @SuppressWarnings("unchecked")
    public PolicyEngine(InputStream configStream) {
        Map<String, Object> cfg = new Yaml().load(configStream);
        this.policies = (Map<String, Object>) cfg.getOrDefault("source_type_policies", Map.of());
    }

    @SuppressWarnings("unchecked")
    public PolicyDecision decide(
            VideoSource source,
            VideoSink sink,
            int fanoutCount,
            boolean crossRoom,
            FabricKind activeEndoscopeFabric) {

        Map<String, Object> raw = (Map<String, Object>) policies.get(source.getSourceType().name());
        if (raw == null) {
            return new PolicyDecision(
                    List.of(FabricKind.SDVOE, FabricKind.MATRIX),
                    List.of(FabricKind.MATRIX),
                    new PolicyConstraints(),
                    "default: unknown source type",
                    0);
        }

        List<FabricKind> preferred = parseFabrics(raw.get("preferred_fabrics"));
        List<FabricKind> fallback = parseFabrics(raw.get("fallback_fabrics"));
        PolicyConstraints constraints = parseConstraints((Map<String, Object>) raw.get("constraints"));
        List<String> reasonParts = new ArrayList<>();
        reasonParts.add(Objects.toString(raw.getOrDefault("description", source.getSourceType().name())));

        List<Map<String, Object>> overrides =
                (List<Map<String, Object>>) raw.getOrDefault("sink_overrides", List.of());
        for (Map<String, Object> override : overrides) {
            if (Objects.equals(override.get("when_sink_role"), sink.getRole().name())) {
                if (override.containsKey("preferred_fabrics")) {
                    preferred = parseFabrics(override.get("preferred_fabrics"));
                    reasonParts.add("sink_override=" + sink.getRole().name());
                }
                if (override.containsKey("constraints")) {
                    constraints = mergeConstraints(
                            constraints, (Map<String, Object>) override.get("constraints"));
                }
            }
        }

        List<Map<String, Object>> rules = (List<Map<String, Object>>) raw.getOrDefault("rules", List.of());
        for (Map<String, Object> rule : rules) {
            String expr = Objects.toString(rule.get("if"), "");
            if (evalRule(expr, source, crossRoom) && rule.containsKey("preferred_fabrics")) {
                preferred = parseFabrics(rule.get("preferred_fabrics"));
                reasonParts.add("rule=" + expr);
            }
        }

        if (constraints.isAllowMulticastFanout()
                && fanoutCount >= constraints.getPreferMulticastWhenSinksGte()) {
            preferred = boostFabric(preferred, FabricKind.SDVOE);
            reasonParts.add("fanout=" + fanoutCount + " -> prefer SDVoE");
        }

        if (sink.isUltraLowLatency() || sink.getRole() == SinkRole.PRIMARY_SURGEON_DISPLAY) {
            if (source.getSourceType() == SourceType.ENDOSCOPE
                    || source.getSourceType() == SourceType.CARM_FLUORO) {
                preferred = boostFabric(preferred, FabricKind.MATRIX);
                constraints.setMaxBridgeHops(Math.min(constraints.getMaxBridgeHops(), 0));
                constraints.setMaxLatencyMs(Math.min(constraints.getMaxLatencyMs(), 40));
                if (constraints.getLockMode() == LockMode.NONE) {
                    constraints.setLockMode(LockMode.HARD);
                }
                reasonParts.add("critical primary display -> MATRIX first");
            }
        }

        if (source.getSourceType() == SourceType.NAVIGATION
                && constraints.getRequireGenlockWith() == SourceType.ENDOSCOPE
                && activeEndoscopeFabric != null) {
            preferred = boostFabric(preferred, activeEndoscopeFabric);
            reasonParts.add("genlock with endoscope on " + activeEndoscopeFabric.name());
        }

        if (constraints.isCapabilityDriven() && !source.getEndpoints().isEmpty()) {
            Set<FabricKind> fabrics = source.getEndpoints().stream()
                    .filter(ep -> ep.isOnline())
                    .map(ep -> ep.getFabric())
                    .collect(Collectors.toCollection(HashSet::new));
            if (fabrics.equals(EnumSet.of(FabricKind.MATRIX))) {
                preferred = List.of(FabricKind.MATRIX);
                reasonParts.add("capability: source only on MATRIX");
            } else if (fabrics.equals(EnumSet.of(FabricKind.SDVOE))) {
                preferred = List.of(FabricKind.SDVOE);
                reasonParts.add("capability: source only on SDVOE");
            }
        }

        return new PolicyDecision(
                preferred,
                fallback,
                constraints,
                String.join(" | ", reasonParts),
                source.isCritical() ? 200 : 100);
    }

    @SuppressWarnings("unchecked")
    private static List<FabricKind> parseFabrics(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        List<String> list = (List<String>) raw;
        List<FabricKind> out = new ArrayList<>();
        for (String item : list) {
            out.add(FabricKind.valueOf(item));
        }
        return out;
    }

    private static PolicyConstraints parseConstraints(Map<String, Object> data) {
        PolicyConstraints c = new PolicyConstraints();
        if (data == null) {
            return c;
        }
        if (data.containsKey("max_latency_ms")) {
            c.setMaxLatencyMs(((Number) data.get("max_latency_ms")).intValue());
        }
        if (data.containsKey("max_bridge_hops")) {
            c.setMaxBridgeHops(((Number) data.get("max_bridge_hops")).intValue());
        }
        if (data.containsKey("require_lossless")) {
            c.setRequireLossless(Boolean.TRUE.equals(data.get("require_lossless")));
        }
        Object genlock = data.get("require_genlock_with");
        if (genlock != null) {
            c.setRequireGenlockWith(SourceType.valueOf(genlock.toString()));
        }
        if (data.containsKey("allow_multicast_fanout")) {
            c.setAllowMulticastFanout(Boolean.TRUE.equals(data.get("allow_multicast_fanout")));
        }
        if (data.containsKey("prefer_multicast_when_sinks_gte")) {
            c.setPreferMulticastWhenSinksGte(
                    ((Number) data.get("prefer_multicast_when_sinks_gte")).intValue());
        }
        if (data.containsKey("lock_mode")) {
            c.setLockMode(LockMode.fromValue(data.get("lock_mode").toString()));
        }
        if (data.containsKey("capability_driven")) {
            c.setCapabilityDriven(Boolean.TRUE.equals(data.get("capability_driven")));
        }
        return c;
    }

    private static PolicyConstraints mergeConstraints(
            PolicyConstraints base, Map<String, Object> override) {
        PolicyConstraints merged = base.copy();
        if (override.containsKey("max_latency_ms")) {
            merged.setMaxLatencyMs(((Number) override.get("max_latency_ms")).intValue());
        }
        if (override.containsKey("max_bridge_hops")) {
            merged.setMaxBridgeHops(((Number) override.get("max_bridge_hops")).intValue());
        }
        if (override.containsKey("require_lossless")) {
            merged.setRequireLossless(Boolean.TRUE.equals(override.get("require_lossless")));
        }
        if (override.containsKey("require_genlock_with")) {
            merged.setRequireGenlockWith(
                    SourceType.valueOf(override.get("require_genlock_with").toString()));
        }
        if (override.containsKey("allow_multicast_fanout")) {
            merged.setAllowMulticastFanout(
                    Boolean.TRUE.equals(override.get("allow_multicast_fanout")));
        }
        if (override.containsKey("prefer_multicast_when_sinks_gte")) {
            merged.setPreferMulticastWhenSinksGte(
                    ((Number) override.get("prefer_multicast_when_sinks_gte")).intValue());
        }
        if (override.containsKey("lock_mode")) {
            merged.setLockMode(LockMode.fromValue(override.get("lock_mode").toString()));
        }
        if (override.containsKey("capability_driven")) {
            merged.setCapabilityDriven(Boolean.TRUE.equals(override.get("capability_driven")));
        }
        return merged;
    }

    private static boolean evalRule(String expr, VideoSource source, boolean crossRoom) {
        if (expr == null || expr.isBlank()) {
            return false;
        }
        if (expr.contains("distance_class == 'NEAR'") && source.getDistanceClass() == DistanceClass.NEAR) {
            return true;
        }
        if (expr.contains("distance_class == 'FAR'") && source.getDistanceClass() == DistanceClass.FAR) {
            return true;
        }
        return expr.contains("cross_room") && crossRoom;
    }

    private static List<FabricKind> boostFabric(List<FabricKind> preferred, FabricKind fabric) {
        List<FabricKind> rest = preferred.stream().filter(f -> f != fabric).collect(Collectors.toList());
        List<FabricKind> out = new ArrayList<>();
        out.add(fabric);
        out.addAll(rest);
        return out;
    }
}
