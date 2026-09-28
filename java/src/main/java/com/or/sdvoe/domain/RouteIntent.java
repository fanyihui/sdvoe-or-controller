package com.or.sdvoe.domain;

import java.util.HashMap;
import java.util.Map;

public class RouteIntent {
    private final String sourceId;
    private final String sinkId;
    private final String operator;
    private String sceneId;
    private LockMode requestLock = LockMode.NONE;
    private Map<String, Object> context = new HashMap<>();

    public RouteIntent(String sourceId, String sinkId, String operator) {
        this.sourceId = sourceId;
        this.sinkId = sinkId;
        this.operator = operator;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSinkId() {
        return sinkId;
    }

    public String getOperator() {
        return operator;
    }

    public String getSceneId() {
        return sceneId;
    }

    public RouteIntent sceneId(String sceneId) {
        this.sceneId = sceneId;
        return this;
    }

    public LockMode getRequestLock() {
        return requestLock;
    }

    public RouteIntent requestLock(LockMode requestLock) {
        this.requestLock = requestLock;
        return this;
    }

    public Map<String, Object> getContext() {
        return context;
    }
}
