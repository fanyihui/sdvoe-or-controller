package com.or.sdvoe.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 工作空间中的逻辑视频源（已关联设备实时状态）。 */
public final class WorkspaceSource {
    private final String id;
    private final String name;
    private final SourceType sourceType;
    private final String deviceId;
    private final boolean critical;
    private final DeviceOnlineStatus deviceStatus;
    private final boolean signalPresent;
    private final String streamId;
    private final String ipAddress;
    private final String location;

    public WorkspaceSource(
            String id,
            String name,
            SourceType sourceType,
            String deviceId,
            boolean critical,
            DeviceOnlineStatus deviceStatus,
            boolean signalPresent,
            String streamId,
            String ipAddress,
            String location) {
        this.id = id;
        this.name = name;
        this.sourceType = sourceType;
        this.deviceId = deviceId;
        this.critical = critical;
        this.deviceStatus = deviceStatus != null ? deviceStatus : DeviceOnlineStatus.UNKNOWN;
        this.signalPresent = signalPresent;
        this.streamId = streamId;
        this.ipAddress = ipAddress;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DeviceOnlineStatus getDeviceStatus() {
        return deviceStatus;
    }

    public boolean isSignalPresent() {
        return signalPresent;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("sourceType", sourceType.name());
        m.put("deviceId", deviceId);
        m.put("critical", critical);
        m.put("deviceStatus", deviceStatus.name());
        m.put("signalPresent", signalPresent);
        m.put("streamId", streamId);
        m.put("ipAddress", ipAddress);
        m.put("location", location);
        m.put("recording", false);
        m.put("recordable", deviceStatus == DeviceOnlineStatus.ONLINE && signalPresent);
        return m;
    }

    public Map<String, Object> toMap(boolean recording, boolean anotherRecordingActive) {
        Map<String, Object> m = toMap();
        m.put("recording", recording);
        boolean recordable = deviceStatus == DeviceOnlineStatus.ONLINE && signalPresent && !anotherRecordingActive;
        if (recording) {
            recordable = false;
        }
        m.put("recordable", recordable);
        return m;
    }
}
