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
    private final String routedSourceId;
    private final String routedSourceName;
    private final String routeId;
    private final String mosaicId;
    private final boolean mosaic;

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
        this(
                id,
                name,
                role,
                deviceId,
                ultraLowLatency,
                deviceStatus,
                signalPresent,
                currentStreamId,
                ipAddress,
                location,
                null,
                null,
                null,
                null,
                false);
    }

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
            String location,
            String routedSourceId,
            String routedSourceName,
            String routeId) {
        this(
                id,
                name,
                role,
                deviceId,
                ultraLowLatency,
                deviceStatus,
                signalPresent,
                currentStreamId,
                ipAddress,
                location,
                routedSourceId,
                routedSourceName,
                routeId,
                null,
                false);
    }

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
            String location,
            String routedSourceId,
            String routedSourceName,
            String routeId,
            String mosaicId,
            boolean mosaic) {
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
        this.routedSourceId = routedSourceId;
        this.routedSourceName = routedSourceName;
        this.routeId = routeId;
        this.mosaicId = mosaicId;
        this.mosaic = mosaic;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WorkspaceDestination withActiveRoute(
            String sourceId, String sourceName, String streamId, String routeId) {
        return new WorkspaceDestination(
                id,
                name,
                role,
                deviceId,
                ultraLowLatency,
                deviceStatus,
                true,
                streamId != null ? streamId : currentStreamId,
                ipAddress,
                location,
                sourceId,
                sourceName,
                routeId,
                null,
                false);
    }

    public WorkspaceDestination withActiveMosaic(
            String mosaicId, String mosaicLabel, String streamId, String routeId) {
        return new WorkspaceDestination(
                id,
                name,
                role,
                deviceId,
                ultraLowLatency,
                deviceStatus,
                true,
                streamId != null ? streamId : currentStreamId,
                ipAddress,
                location,
                mosaicId,
                mosaicLabel,
                routeId,
                mosaicId,
                true);
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
        m.put("routedSourceId", routedSourceId);
        m.put("routedSourceName", routedSourceName);
        m.put("routeId", routeId);
        m.put("mosaicId", mosaicId);
        m.put("mosaic", mosaic);
        m.put("routed", routedSourceId != null);
        return m;
    }
}
