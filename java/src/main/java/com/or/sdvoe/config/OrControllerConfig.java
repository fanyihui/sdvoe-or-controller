package com.or.sdvoe.config;

import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceRole;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 加载手术室与 SDVoE 发现相关配置。 */
public final class OrControllerConfig {

    private final OperatingRoom operatingRoom;
    private final String discoveryMode;
    private final String managerBaseUrl;
    private final List<SdvoeDevice> inventoryDevices;
    private final String databasePath;
    private final boolean restoreRoutesOnStartup;

    @SuppressWarnings("unchecked")
    private OrControllerConfig(Map<String, Object> root) {
        Map<String, Object> or = (Map<String, Object>) root.getOrDefault("operating_room", Map.of());
        this.operatingRoom = new OperatingRoom(
                Objects.toString(or.getOrDefault("id", "OR-01")),
                Objects.toString(or.getOrDefault("name", "手术室")),
                or.get("building") == null ? null : or.get("building").toString(),
                or.get("floor") == null ? null : or.get("floor").toString());

        Map<String, Object> sdvoe = (Map<String, Object>) root.getOrDefault("sdvoe", Map.of());
        Map<String, Object> discovery = (Map<String, Object>) sdvoe.getOrDefault("discovery", Map.of());
        this.discoveryMode = Objects.toString(discovery.getOrDefault("mode", "inventory")).toLowerCase();
        this.managerBaseUrl = discovery.get("manager_base_url") == null
                ? null
                : discovery.get("manager_base_url").toString();

        List<Map<String, Object>> rawDevices =
                (List<Map<String, Object>>) sdvoe.getOrDefault("inventory", List.of());
        List<SdvoeDevice> devices = new ArrayList<>();
        for (Map<String, Object> raw : rawDevices) {
            devices.add(parseDevice(raw, this.operatingRoom.getId()));
        }
        this.inventoryDevices = List.copyOf(devices);

        Map<String, Object> database = (Map<String, Object>) root.getOrDefault("database", Map.of());
        this.databasePath = Objects.toString(database.getOrDefault("path", "data/or-desk.db"));
        this.restoreRoutesOnStartup = !Boolean.FALSE.equals(database.get("restore_routes_on_startup"));
    }

    public static OrControllerConfig load(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config: " + path, e);
        }
    }

    public static OrControllerConfig load(InputStream in) {
        Map<String, Object> root = new Yaml().load(in);
        if (root == null) {
            root = Map.of();
        }
        return new OrControllerConfig(root);
    }

    public static OrControllerConfig loadClasspath(String resource) {
        InputStream in = OrControllerConfig.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Classpath resource not found: " + resource);
        }
        return load(in);
    }

    @SuppressWarnings("unchecked")
    private static SdvoeDevice parseDevice(Map<String, Object> raw, String defaultOrId) {
        String id = Objects.requireNonNull(raw.get("id"), "device.id").toString();
        String orId = Objects.toString(raw.getOrDefault("or_id", defaultOrId));
        SdvoeDeviceRole role = SdvoeDeviceRole.valueOf(
                Objects.toString(raw.getOrDefault("role", "UNKNOWN")).toUpperCase());
        DeviceOnlineStatus status = DeviceOnlineStatus.valueOf(
                Objects.toString(raw.getOrDefault("status", "UNKNOWN")).toUpperCase());

        Map<String, String> tags = new LinkedHashMap<>();
        Object rawTags = raw.get("tags");
        if (rawTags instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    tags.put(k.toString(), v.toString());
                }
            });
        }

        return SdvoeDevice.builder(id, orId)
                .name(raw.get("name") == null ? id : raw.get("name").toString())
                .role(role)
                .ipAddress(asString(raw.get("ip")))
                .macAddress(asString(raw.get("mac")))
                .model(asString(raw.get("model")))
                .firmware(asString(raw.get("firmware")))
                .location(asString(raw.get("location")))
                .status(status)
                .signalPresent(Boolean.TRUE.equals(raw.get("signal_present")))
                .streamId(asString(raw.get("stream_id")))
                .tags(tags)
                .build();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public OperatingRoom getOperatingRoom() {
        return operatingRoom;
    }

    public String getDiscoveryMode() {
        return discoveryMode;
    }

    public String getManagerBaseUrl() {
        return managerBaseUrl;
    }

    public List<SdvoeDevice> getInventoryDevices() {
        return inventoryDevices;
    }

    public List<SdvoeDevice> inventoryForCurrentOr() {
        String orId = operatingRoom.getId();
        List<SdvoeDevice> filtered = new ArrayList<>();
        for (SdvoeDevice d : inventoryDevices) {
            if (orId.equals(d.getOrId())) {
                filtered.add(d);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public boolean isRestoreRoutesOnStartup() {
        return restoreRoutesOnStartup;
    }
}
