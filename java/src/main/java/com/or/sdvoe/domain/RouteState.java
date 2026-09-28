package com.or.sdvoe.domain;

public class RouteState {
    private final String routeId;
    private final String sourceId;
    private final String sinkId;
    private final RoutePlan plan;
    private boolean active = true;
    private LockMode lockMode = LockMode.NONE;
    private int version = 1;

    public RouteState(String routeId, String sourceId, String sinkId, RoutePlan plan) {
        this.routeId = routeId;
        this.sourceId = sourceId;
        this.sinkId = sinkId;
        this.plan = plan;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSinkId() {
        return sinkId;
    }

    public RoutePlan getPlan() {
        return plan;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LockMode getLockMode() {
        return lockMode;
    }

    public RouteState lockMode(LockMode lockMode) {
        this.lockMode = lockMode;
        return this;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
