package com.or.sdvoe.planner;

import com.or.sdvoe.domain.FabricKind;

import java.util.HashMap;
import java.util.Map;

public class GraphEdge {
    private final String src;
    private final String dst;
    private final FabricKind fabric;
    private final int latencyMs;
    private final double reliability;
    private final boolean bridge;
    private final boolean exclusive;
    private final Map<String, Object> meta;

    public GraphEdge(
            String src,
            String dst,
            FabricKind fabric,
            int latencyMs,
            double reliability,
            boolean bridge,
            Map<String, Object> meta) {
        this.src = src;
        this.dst = dst;
        this.fabric = fabric;
        this.latencyMs = latencyMs;
        this.reliability = reliability;
        this.bridge = bridge;
        this.exclusive = false;
        this.meta = meta != null ? new HashMap<>(meta) : new HashMap<>();
    }

    public static GraphEdge of(
            String src, String dst, FabricKind fabric, int latencyMs, double reliability, Map<String, Object> meta) {
        return new GraphEdge(src, dst, fabric, latencyMs, reliability, false, meta);
    }

    public String getSrc() {
        return src;
    }

    public String getDst() {
        return dst;
    }

    public FabricKind getFabric() {
        return fabric;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public double getReliability() {
        return reliability;
    }

    public boolean isBridge() {
        return bridge;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
