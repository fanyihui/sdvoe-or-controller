package com.or.sdvoe.planner;

import com.or.sdvoe.domain.FabricKind;

public class GraphNode {
    private final String id;
    private final String kind;
    private final FabricKind fabric;
    private final String ref;

    public GraphNode(String id, String kind, FabricKind fabric) {
        this(id, kind, fabric, "");
    }

    public GraphNode(String id, String kind, FabricKind fabric, String ref) {
        this.id = id;
        this.kind = kind;
        this.fabric = fabric;
        this.ref = ref;
    }

    public String getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public FabricKind getFabric() {
        return fabric;
    }

    public String getRef() {
        return ref;
    }
}
