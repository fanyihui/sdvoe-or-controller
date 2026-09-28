package com.or.sdvoe.domain;

import java.util.HashMap;
import java.util.Map;

public class FabricStep {
    private final FabricKind fabric;
    private final String action;
    private final Map<String, Object> params;
    private String compensationAction;
    private Map<String, Object> compensationParams;

    public FabricStep(FabricKind fabric, String action, Map<String, Object> params) {
        this.fabric = fabric;
        this.action = action;
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    public FabricKind getFabric() {
        return fabric;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getCompensationAction() {
        return compensationAction;
    }

    public FabricStep compensationAction(String compensationAction) {
        this.compensationAction = compensationAction;
        return this;
    }

    public Map<String, Object> getCompensationParams() {
        return compensationParams;
    }

    public FabricStep compensationParams(Map<String, Object> compensationParams) {
        this.compensationParams = compensationParams;
        return this;
    }
}
