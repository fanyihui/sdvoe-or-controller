package com.or.sdvoe.schedule;

import com.or.sdvoe.domain.CaseStatus;
import com.or.sdvoe.domain.Patient;
import com.or.sdvoe.domain.SinkRole;
import com.or.sdvoe.domain.SourceType;
import com.or.sdvoe.domain.SurgeryCase;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 从 YAML 加载手术排班与逻辑源/目的地定义。 */
public final class ScheduleRepository {

    public record LogicalSourceDef(
            String id, String name, SourceType sourceType, String deviceId, boolean critical) {
    }

    public record LogicalDestinationDef(
            String id, String name, SinkRole role, String deviceId, boolean ultraLowLatency) {
    }

    private final String operatingRoomId;
    private final String date;
    private final List<SurgeryCase> cases;
    private final List<LogicalSourceDef> sources;
    private final List<LogicalDestinationDef> destinations;

    @SuppressWarnings("unchecked")
    private ScheduleRepository(Map<String, Object> root) {
        this.operatingRoomId = Objects.toString(root.getOrDefault("operating_room_id", "OR-01"));
        this.date = Objects.toString(root.getOrDefault("date", ""));
        List<Map<String, Object>> rawCases =
                (List<Map<String, Object>>) root.getOrDefault("cases", List.of());
        List<SurgeryCase> loaded = new ArrayList<>();
        for (Map<String, Object> raw : rawCases) {
            loaded.add(parseCase(raw, operatingRoomId, date));
        }
        loaded.sort(Comparator.comparing(SurgeryCase::getScheduledStart, Comparator.nullsLast(String::compareTo)));
        this.cases = List.copyOf(loaded);

        List<LogicalSourceDef> src = new ArrayList<>();
        for (Map<String, Object> raw :
                (List<Map<String, Object>>) root.getOrDefault("logical_sources", List.of())) {
            src.add(new LogicalSourceDef(
                    raw.get("id").toString(),
                    Objects.toString(raw.getOrDefault("name", raw.get("id"))),
                    SourceType.valueOf(raw.get("source_type").toString()),
                    asString(raw.get("device_id")),
                    Boolean.TRUE.equals(raw.get("critical"))));
        }
        this.sources = List.copyOf(src);

        List<LogicalDestinationDef> dst = new ArrayList<>();
        for (Map<String, Object> raw :
                (List<Map<String, Object>>) root.getOrDefault("logical_destinations", List.of())) {
            dst.add(new LogicalDestinationDef(
                    raw.get("id").toString(),
                    Objects.toString(raw.getOrDefault("name", raw.get("id"))),
                    SinkRole.valueOf(raw.get("role").toString()),
                    asString(raw.get("device_id")),
                    Boolean.TRUE.equals(raw.get("ultra_low_latency"))));
        }
        this.destinations = List.copyOf(dst);
    }

    public static ScheduleRepository loadClasspath(String resource) {
        InputStream in = ScheduleRepository.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("schedule resource not found: " + resource);
        }
        return load(in);
    }

    public static ScheduleRepository load(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schedule: " + path, e);
        }
    }

    public static ScheduleRepository load(InputStream in) {
        Map<String, Object> root = new Yaml().load(in);
        if (root == null) {
            root = new HashMap<>();
        }
        return new ScheduleRepository(root);
    }

    @SuppressWarnings("unchecked")
    private static SurgeryCase parseCase(Map<String, Object> raw, String orId, String date) {
        Map<String, Object> p = (Map<String, Object>) raw.get("patient");
        List<String> allergies = new ArrayList<>();
        Object rawAllergies = p.get("allergies");
        if (rawAllergies instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    allergies.add(item.toString());
                }
            }
        }
        Patient patient = new Patient(
                p.get("id").toString(),
                p.get("name").toString(),
                asString(p.get("gender")),
                p.get("age") == null ? 0 : ((Number) p.get("age")).intValue(),
                asString(p.get("bed")),
                asString(p.get("blood_type")),
                allergies,
                asString(p.get("diagnosis")));

        return SurgeryCase.builder(raw.get("id").toString(), orId, patient)
                .date(date)
                .status(CaseStatus.valueOf(raw.get("status").toString()))
                .scheduledStart(asString(raw.get("scheduled_start")))
                .scheduledEnd(asString(raw.get("scheduled_end")))
                .actualStart(asString(raw.get("actual_start")))
                .actualEnd(asString(raw.get("actual_end")))
                .procedure(asString(raw.get("procedure")))
                .procedureCode(asString(raw.get("procedure_code")))
                .department(asString(raw.get("department")))
                .surgeon(asString(raw.get("surgeon")))
                .anesthetist(asString(raw.get("anesthetist")))
                .scrubNurse(asString(raw.get("scrub_nurse")))
                .circulatingNurse(asString(raw.get("circulating_nurse")))
                .notes(asString(raw.get("notes")))
                .build();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public String getOperatingRoomId() {
        return operatingRoomId;
    }

    public String getDate() {
        return date;
    }

    public List<SurgeryCase> getCases() {
        return cases;
    }

    public Optional<SurgeryCase> findCase(String caseId) {
        return cases.stream().filter(c -> c.getId().equals(caseId)).findFirst();
    }

    public List<LogicalSourceDef> getSources() {
        return sources;
    }

    public List<LogicalDestinationDef> getDestinations() {
        return destinations;
    }
}
