package com.or.sdvoe.domain;

import java.util.ArrayList;
import java.util.List;

public class PolicyDecision {
    private List<FabricKind> preferredFabrics;
    private List<FabricKind> fallbackFabrics;
    private PolicyConstraints constraints;
    private String reason;
    private int priority = 100;

    public PolicyDecision(
            List<FabricKind> preferredFabrics,
            List<FabricKind> fallbackFabrics,
            PolicyConstraints constraints,
            String reason,
            int priority) {
        this.preferredFabrics = new ArrayList<>(preferredFabrics);
        this.fallbackFabrics = new ArrayList<>(fallbackFabrics);
        this.constraints = constraints;
        this.reason = reason;
        this.priority = priority;
    }

    public List<FabricKind> getPreferredFabrics() {
        return preferredFabrics;
    }

    public void setPreferredFabrics(List<FabricKind> preferredFabrics) {
        this.preferredFabrics = preferredFabrics;
    }

    public List<FabricKind> getFallbackFabrics() {
        return fallbackFabrics;
    }

    public PolicyConstraints getConstraints() {
        return constraints;
    }

    public String getReason() {
        return reason;
    }

    public int getPriority() {
        return priority;
    }
}
