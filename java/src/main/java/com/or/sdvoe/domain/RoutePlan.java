package com.or.sdvoe.domain;

import java.util.ArrayList;
import java.util.List;

public class RoutePlan {
    private final RouteIntent intent;
    private final List<FabricStep> steps;
    private final int estimatedLatencyMs;
    private final List<FabricKind> fabricsUsed;
    private final PolicyDecision decision;
    private List<String> pathNodeIds = new ArrayList<>();

    public RoutePlan(
            RouteIntent intent,
            List<FabricStep> steps,
            int estimatedLatencyMs,
            List<FabricKind> fabricsUsed,
            PolicyDecision decision) {
        this.intent = intent;
        this.steps = new ArrayList<>(steps);
        this.estimatedLatencyMs = estimatedLatencyMs;
        this.fabricsUsed = new ArrayList<>(fabricsUsed);
        this.decision = decision;
    }

    public RouteIntent getIntent() {
        return intent;
    }

    public List<FabricStep> getSteps() {
        return steps;
    }

    public int getEstimatedLatencyMs() {
        return estimatedLatencyMs;
    }

    public List<FabricKind> getFabricsUsed() {
        return fabricsUsed;
    }

    public PolicyDecision getDecision() {
        return decision;
    }

    public List<String> getPathNodeIds() {
        return pathNodeIds;
    }

    public RoutePlan pathNodeIds(List<String> pathNodeIds) {
        this.pathNodeIds = new ArrayList<>(pathNodeIds);
        return this;
    }
}
