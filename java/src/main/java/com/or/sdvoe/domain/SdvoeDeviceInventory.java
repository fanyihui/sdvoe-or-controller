package com.or.sdvoe.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 本手术室 SDVoE 设备清单快照。 */
public final class SdvoeDeviceInventory {

    private final OperatingRoom operatingRoom;
    private final List<SdvoeDevice> devices;
    private final Instant discoveredAt;
    private final String discoverySource;

    public SdvoeDeviceInventory(
            OperatingRoom operatingRoom,
            List<SdvoeDevice> devices,
            Instant discoveredAt,
            String discoverySource) {
        this.operatingRoom = operatingRoom;
        this.devices = List.copyOf(devices);
        this.discoveredAt = discoveredAt;
        this.discoverySource = discoverySource;
    }

    public OperatingRoom getOperatingRoom() {
        return operatingRoom;
    }

    public List<SdvoeDevice> getDevices() {
        return devices;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public String getDiscoverySource() {
        return discoverySource;
    }

    public long onlineCount() {
        return devices.stream().filter(d -> d.getStatus() == DeviceOnlineStatus.ONLINE).count();
    }

    public Map<SdvoeDeviceRole, Long> countByRole() {
        return devices.stream()
                .collect(Collectors.groupingBy(SdvoeDevice::getRole, Collectors.counting()));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> or = new LinkedHashMap<>();
        or.put("id", operatingRoom.getId());
        or.put("name", operatingRoom.getName());
        or.put("building", operatingRoom.getBuilding());
        or.put("floor", operatingRoom.getFloor());
        m.put("operatingRoom", or);
        m.put("discoveredAt", discoveredAt.toString());
        m.put("discoverySource", discoverySource);
        m.put("total", devices.size());
        m.put("online", onlineCount());
        m.put("byRole", countByRole().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)));
        List<Map<String, Object>> deviceMaps = new ArrayList<>();
        for (SdvoeDevice d : devices) {
            deviceMaps.add(d.toMap());
        }
        m.put("devices", deviceMaps);
        return m;
    }
}
