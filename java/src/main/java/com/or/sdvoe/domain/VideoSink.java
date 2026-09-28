package com.or.sdvoe.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoSink {
    private final String id;
    private final String name;
    private final SinkRole role;
    private final List<PhysicalEndpoint> endpoints;
    private boolean ultraLowLatency;
    private Map<String, Object> metadata = new HashMap<>();

    public VideoSink(String id, String name, SinkRole role, List<PhysicalEndpoint> endpoints) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.endpoints = new ArrayList<>(endpoints);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SinkRole getRole() {
        return role;
    }

    public List<PhysicalEndpoint> getEndpoints() {
        return endpoints;
    }

    public boolean isUltraLowLatency() {
        return ultraLowLatency;
    }

    public VideoSink ultraLowLatency(boolean ultraLowLatency) {
        this.ultraLowLatency = ultraLowLatency;
        return this;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
