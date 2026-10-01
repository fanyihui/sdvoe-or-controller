package com.or.sdvoe.workspace;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作空间内一条已建立的逻辑路由。 */
public final class ActiveRoute {
    private final String routeId;
    private final String caseId;
    private final String sourceId;
    private final String sourceName;
    private final String destinationId;
    private final String destinationName;
    private final String streamId;
    private final List<String> fabrics;
    private final String policyReason;
    private final String operator;
    private final Instant createdAt;

    public ActiveRoute(
            String routeId,
            String caseId,
            String sourceId,
            String sourceName,
            String destinationId,
            String destinationName,
            String streamId,
            List<String> fabrics,
            String policyReason,
            String operator) {
        this.routeId = routeId;
        this.caseId = caseId;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.destinationId = destinationId;
        this.destinationName = destinationName;
        this.streamId = streamId;
        this.fabrics = List.copyOf(fabrics);
        this.policyReason = policyReason;
        this.operator = operator;
        this.createdAt = Instant.now();
    }

    public String getRouteId() {
        return routeId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getStreamId() {
        return streamId;
    }

    public List<String> getFabrics() {
        return fabrics;
    }

    public String getPolicyReason() {
        return policyReason;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", routeId);
        m.put("caseId", caseId);
        m.put("sourceId", sourceId);
        m.put("sourceName", sourceName);
        m.put("destinationId", destinationId);
        m.put("destinationName", destinationName);
        m.put("streamId", streamId);
        m.put("fabrics", fabrics);
        m.put("policyReason", policyReason);
        m.put("operator", operator);
        m.put("createdAt", createdAt.toString());
        return m;
    }
}
