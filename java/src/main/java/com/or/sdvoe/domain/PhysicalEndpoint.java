package com.or.sdvoe.domain;

import java.util.HashMap;
import java.util.Map;

public class PhysicalEndpoint {
    private final String id;
    private final FabricKind fabric;
    private final String deviceId;
    private final String port;
    private final String direction;
    private boolean online = true;
    private SignalCapability capabilities = new SignalCapability();
    private Map<String, String> labels = new HashMap<>();

    public PhysicalEndpoint(String id, FabricKind fabric, String deviceId, String port, String direction) {
        this.id = id;
        this.fabric = fabric;
        this.deviceId = deviceId;
        this.port = port;
        this.direction = direction;
    }

    public String getId() {
        return id;
    }

    public FabricKind getFabric() {
        return fabric;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPort() {
        return port;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public SignalCapability getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(SignalCapability capabilities) {
        this.capabilities = capabilities;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
}
