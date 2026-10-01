package com.or.sdvoe.workspace;

import com.or.sdvoe.adapter.BridgeAdapter;
import com.or.sdvoe.adapter.FabricAdapter;
import com.or.sdvoe.adapter.MatrixAdapter;
import com.or.sdvoe.adapter.SDVoEAdapter;
import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.PhysicalEndpoint;
import com.or.sdvoe.domain.PolicyDecision;
import com.or.sdvoe.domain.RouteIntent;
import com.or.sdvoe.domain.RoutePlan;
import com.or.sdvoe.domain.RouteState;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.domain.VideoSink;
import com.or.sdvoe.domain.VideoSource;
import com.or.sdvoe.policy.PolicyEngine;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.FabricOrchestrator;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手术工作空间拖拽路由：逻辑源 → 逻辑目的地。
 * 通过策略引擎给出通路偏好，并经 SDVoE Adapter 下发 subscribe。
 */
public class WorkspaceRoutingService {

    private final ScheduleService scheduleService;
    private final SdvoeDeviceInventoryService deviceInventoryService;
    private final PolicyEngine policyEngine;
    private final FabricOrchestrator orchestrator;
    /** caseId -> (destinationId -> route) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ActiveRoute>> routesByCase =
            new ConcurrentHashMap<>();

    public WorkspaceRoutingService(
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            PolicyEngine policyEngine,
            FabricOrchestrator orchestrator) {
        this.scheduleService = Objects.requireNonNull(scheduleService);
        this.deviceInventoryService = Objects.requireNonNull(deviceInventoryService);
        this.policyEngine = Objects.requireNonNull(policyEngine);
        this.orchestrator = Objects.requireNonNull(orchestrator);
    }

    public static FabricOrchestrator createDefaultOrchestrator() {
        Map<FabricKind, FabricAdapter> adapters = new LinkedHashMap<>();
        adapters.put(FabricKind.MATRIX, new MatrixAdapter(null));
        adapters.put(FabricKind.SDVOE, new SDVoEAdapter(null));
        adapters.put(FabricKind.BRIDGE, new BridgeAdapter());
        return new FabricOrchestrator(adapters);
    }

    public List<ActiveRoute> listRoutes(String caseId) {
        scheduleService.getCase(caseId); // validate
        ConcurrentHashMap<String, ActiveRoute> map = routesByCase.get(caseId);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return List.copyOf(map.values());
    }

    public ActiveRoute getRouteForDestination(String caseId, String destinationId) {
        ConcurrentHashMap<String, ActiveRoute> map = routesByCase.get(caseId);
        if (map == null) {
            return null;
        }
        return map.get(destinationId);
    }

    public ActiveRoute route(
            String caseId,
            String sourceId,
            String destinationId,
            String operator,
            boolean confirmed) {
        scheduleService.getCase(caseId);
        ScheduleRepository repo = scheduleService.repository();

        ScheduleRepository.LogicalSourceDef sourceDef = repo.getSources().stream()
                .filter(s -> s.id().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("source not found: " + sourceId));
        ScheduleRepository.LogicalDestinationDef destDef = repo.getDestinations().stream()
                .filter(d -> d.id().equals(destinationId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("destination not found: " + destinationId));

        ActiveRoute existing = getRouteForDestination(caseId, destinationId);
        if (existing != null && !confirmed && !existing.getSourceId().equals(sourceId)) {
            throw new RouteConflictException(existing);
        }

        SdvoeDeviceInventory inventory = deviceInventoryService.listCurrentOrDevices();
        Map<String, SdvoeDevice> byId = new LinkedHashMap<>();
        for (SdvoeDevice d : inventory.getDevices()) {
            byId.put(d.getId(), d);
        }
        SdvoeDevice enc = byId.get(sourceDef.deviceId());
        SdvoeDevice dec = byId.get(destDef.deviceId());
        if (enc == null) {
            throw new IllegalStateException("source device offline/missing: " + sourceDef.deviceId());
        }
        if (dec == null) {
            throw new IllegalStateException("destination device offline/missing: " + destDef.deviceId());
        }
        if (enc.getStatus() == DeviceOnlineStatus.OFFLINE) {
            throw new IllegalStateException("source device OFFLINE: " + enc.getId());
        }
        if (dec.getStatus() == DeviceOnlineStatus.OFFLINE) {
            throw new IllegalStateException("destination device OFFLINE: " + dec.getId());
        }

        String streamId = enc.getStreamId() != null && !enc.getStreamId().isBlank()
                ? enc.getStreamId()
                : "stream-" + sourceDef.id();

        PolicyDecision decision = decide(sourceDef, destDef);
        List<FabricStep> steps = buildSteps(enc, dec, streamId, decision);
        RouteIntent intent = new RouteIntent(sourceDef.id(), destDef.id(), operator == null ? "or-desk" : operator)
                .sceneId(caseId);
        RoutePlan plan = new RoutePlan(
                intent,
                steps,
                estimateLatency(decision),
                fabricsUsed(decision),
                decision);

        RouteState state = orchestrator.execute(plan, "steal_with_confirm", true);

        ActiveRoute active = new ActiveRoute(
                state.getRouteId(),
                caseId,
                sourceDef.id(),
                sourceDef.name(),
                destDef.id(),
                destDef.name(),
                streamId,
                plan.getFabricsUsed().stream().map(Enum::name).toList(),
                decision.getReason(),
                intent.getOperator());

        routesByCase
                .computeIfAbsent(caseId, k -> new ConcurrentHashMap<>())
                .put(destinationId, active);
        return active;
    }

    public void clearRoute(String caseId, String destinationId) {
        ConcurrentHashMap<String, ActiveRoute> map = routesByCase.get(caseId);
        if (map == null) {
            return;
        }
        ActiveRoute removed = map.remove(destinationId);
        if (removed != null) {
            orchestrator.release(removed.getRouteId());
        }
    }

    private PolicyDecision decide(
            ScheduleRepository.LogicalSourceDef sourceDef,
            ScheduleRepository.LogicalDestinationDef destDef) {
        PhysicalEndpoint srcEp = new PhysicalEndpoint(
                "ep." + sourceDef.deviceId(), FabricKind.SDVOE, sourceDef.deviceId(), "IN", "in");
        PhysicalEndpoint dstEp = new PhysicalEndpoint(
                "ep." + destDef.deviceId(), FabricKind.SDVOE, destDef.deviceId(), "OUT", "out");
        VideoSource vs = new VideoSource(
                        sourceDef.id(), sourceDef.name(), sourceDef.sourceType(), List.of(srcEp))
                .critical(sourceDef.critical());
        VideoSink sink = new VideoSink(
                        destDef.id(), destDef.name(), destDef.role(), List.of(dstEp))
                .ultraLowLatency(destDef.ultraLowLatency());
        return policyEngine.decide(vs, sink, 1, false, null);
    }

    private List<FabricStep> buildSteps(
            SdvoeDevice enc, SdvoeDevice dec, String streamId, PolicyDecision decision) {
        List<FabricKind> preferred = decision.getPreferredFabrics();
        FabricKind primary = preferred.isEmpty() ? FabricKind.SDVOE : preferred.get(0);

        // 当前逻辑源/目的地均挂在 SDVoE 设备上；Matrix 偏好仍走 SDVoE 直连并记录策略原因
        List<FabricStep> steps = new ArrayList<>();
        if (primary == FabricKind.MATRIX) {
            // 无矩阵交叉点映射时降级 SDVoE，策略原因保留供 UI 展示
            primary = FabricKind.SDVOE;
        }
        if (primary == FabricKind.SDVOE || primary == FabricKind.BRIDGE) {
            Map<String, Object> setStream = new LinkedHashMap<>();
            setStream.put("encoder_id", enc.getId());
            setStream.put("stream_id", streamId);
            setStream.put("mode", "multicast");
            steps.add(new FabricStep(FabricKind.SDVOE, "set_stream", setStream));

            Map<String, Object> subscribe = new LinkedHashMap<>();
            subscribe.put("decoder_id", dec.getId());
            subscribe.put("stream_id", streamId);
            steps.add(new FabricStep(FabricKind.SDVOE, "subscribe", subscribe));
        }
        if (steps.isEmpty()) {
            throw new IllegalStateException("unsupported fabric for drag route: " + primary);
        }
        return steps;
    }

    private static int estimateLatency(PolicyDecision decision) {
        if (decision.getPreferredFabrics().contains(FabricKind.MATRIX)) {
            return 20;
        }
        return 40;
    }

    private static List<FabricKind> fabricsUsed(PolicyDecision decision) {
        List<FabricKind> preferred = decision.getPreferredFabrics();
        if (preferred.contains(FabricKind.SDVOE) || preferred.isEmpty()) {
            return List.of(FabricKind.SDVOE);
        }
        // Matrix-preferred but executed via SDVoE device path in this room layout
        return List.of(FabricKind.SDVOE);
    }
}
