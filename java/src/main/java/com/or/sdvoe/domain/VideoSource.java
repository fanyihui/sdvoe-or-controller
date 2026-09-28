package com.or.sdvoe.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoSource {
    private final String id;
    private final String name;
    private final SourceType sourceType;
    private final List<PhysicalEndpoint> endpoints;
    private DistanceClass distanceClass = DistanceClass.NEAR;
    private boolean critical;
    private Map<String, Object> metadata = new HashMap<>();

    public VideoSource(String id, String name, SourceType sourceType, List<PhysicalEndpoint> endpoints) {
        this.id = id;
        this.name = name;
        this.sourceType = sourceType;
        this.endpoints = new ArrayList<>(endpoints);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public List<PhysicalEndpoint> getEndpoints() {
        return endpoints;
    }

    public DistanceClass getDistanceClass() {
        return distanceClass;
    }

    public VideoSource distanceClass(DistanceClass distanceClass) {
        this.distanceClass = distanceClass;
        return this;
    }

    public boolean isCritical() {
        return critical;
    }

    public VideoSource critical(boolean critical) {
        this.critical = critical;
        return this;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
