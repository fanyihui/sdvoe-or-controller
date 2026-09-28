package com.or.sdvoe.discovery;

import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.Yaml;

/**
 * 通过 SDVoE Manager HTTP API 拉取设备（厂商网关适配骨架）。
 * <p>
 * 约定 Manager 返回 JSON/YAML 列表字段 devices[]，每项至少含 id/role/ip；
 * 实际厂商协议可在此类中替换解析逻辑。
 */
public final class HttpSdvoeManagerDiscovery implements SdvoeDeviceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(HttpSdvoeManagerDiscovery.class);

    private final String baseUrl;
    private final HttpClient client;

    public HttpSdvoeManagerDiscovery(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/$", "");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public String sourceName() {
        return "http-manager:" + baseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SdvoeDevice> discover(OperatingRoom room) {
        String url = baseUrl + "/api/v1/rooms/" + room.getId() + "/devices";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json, application/yaml, text/yaml")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("SDVoE manager HTTP " + response.statusCode() + " for " + url);
            }
            Object loaded = new Yaml().load(response.body());
            List<Map<String, Object>> rawDevices = extractDevices(loaded);
            List<SdvoeDevice> devices = new ArrayList<>();
            Instant now = Instant.now();
            for (Map<String, Object> raw : rawDevices) {
                devices.add(mapDevice(raw, room.getId(), now));
            }
            return devices;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying SDVoE manager", e);
        } catch (Exception e) {
            log.error("Failed to discover from SDVoE manager {}: {}", baseUrl, e.getMessage());
            throw new IllegalStateException("SDVoE manager discovery failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractDevices(Object loaded) {
        if (loaded instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (loaded instanceof Map<?, ?> map && map.get("devices") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static SdvoeDevice mapDevice(Map<String, Object> raw, String defaultOrId, Instant now) {
        String id = Objects.requireNonNull(raw.get("id"), "id").toString();
        String orId = Objects.toString(raw.getOrDefault("orId", raw.getOrDefault("or_id", defaultOrId)));
        SdvoeDeviceRole role = SdvoeDeviceRole.valueOf(
                Objects.toString(raw.getOrDefault("role", "UNKNOWN")).toUpperCase());
        DeviceOnlineStatus status = DeviceOnlineStatus.valueOf(
                Objects.toString(raw.getOrDefault("status", "UNKNOWN")).toUpperCase());
        return SdvoeDevice.builder(id, orId)
                .name(Objects.toString(raw.getOrDefault("name", id)))
                .role(role)
                .ipAddress(asString(raw.get("ip") != null ? raw.get("ip") : raw.get("ipAddress")))
                .macAddress(asString(raw.get("mac") != null ? raw.get("mac") : raw.get("macAddress")))
                .model(asString(raw.get("model")))
                .firmware(asString(raw.get("firmware")))
                .location(asString(raw.get("location")))
                .status(status)
                .signalPresent(Boolean.TRUE.equals(raw.get("signalPresent"))
                        || Boolean.TRUE.equals(raw.get("signal_present")))
                .streamId(asString(raw.get("streamId") != null ? raw.get("streamId") : raw.get("stream_id")))
                .lastSeenAt(now)
                .tags(toStringMap(raw.get("tags")))
                .build();
    }

    private static Map<String, String> toStringMap(Object rawTags) {
        Map<String, String> tags = new java.util.LinkedHashMap<>();
        if (rawTags instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    tags.put(k.toString(), v.toString());
                }
            });
        }
        return tags;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
