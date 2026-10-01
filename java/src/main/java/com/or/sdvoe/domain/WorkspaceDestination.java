package com.or.sdvoe.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 工作空间中的逻辑输出目的地。 */
public final class WorkspaceDestination {
    private final String id;
    private final String name;
    private final SinkRole role;
    private final String deviceId;
    private final boolean ultraLowLatency;
    private final DeviceOnlineStatus deviceStatus;
    private final boolean signalPresent;
    private final String currentStreamId;
    private final String ipAddress;
    private final String location;

    public WorkspaceDestination(
            String id,
            String name,
            SinkRole role,
            String deviceId,
            boolean ultraLowLatency,
            DeviceOnlineStatus deviceStatus,
            boolean signalPresent,
            String currentStreamId,
            String ipAddress,
            String location) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.deviceId = deviceId;
        this.ultraLowLatency = ultraLowLatency;
        this.deviceStatus = deviceStatus != null ? deviceStatus : DeviceOnlineStatus.UNKNOWN;
        this.signalPresent = signalPresent;
        this.currentStreamId = currentStreamId;
        this.ipAddress = ipAddress;
        this.location = location;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("role", role.name());
        m.put("deviceId", deviceId);
        m.put("ultraLowLatency", ultraLowLatency);
        m.put("deviceStatus", deviceStatus.name());
        m.put("signalPresent", signalPresent);
        m.put("currentStreamId", currentStreamId);
        m.put("ipAddress", ipAddress);
        m.put("location", location);
        return m;
    }
}
