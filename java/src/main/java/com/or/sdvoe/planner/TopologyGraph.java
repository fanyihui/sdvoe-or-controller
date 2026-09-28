package com.or.sdvoe.planner;

import com.or.sdvoe.domain.VideoSink;
import com.or.sdvoe.domain.VideoSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TopologyGraph {
    private final Map<String, GraphNode> nodes = new HashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Map<String, List<GraphEdge>> adjacency = new HashMap<>();

    public void addNode(GraphNode node) {
        nodes.put(node.getId(), node);
        adjacency.computeIfAbsent(node.getId(), k -> new ArrayList<>());
    }

    public void addEdge(GraphEdge edge) {
        edges.add(edge);
        adjacency.computeIfAbsent(edge.getSrc(), k -> new ArrayList<>()).add(edge);
        adjacency.computeIfAbsent(edge.getDst(), k -> new ArrayList<>());
    }

    public List<String> endpointsForSource(VideoSource source) {
        return source.getEndpoints().stream()
                .filter(ep -> ep.isOnline())
                .map(ep -> ep.getId())
                .collect(Collectors.toList());
    }

    public List<String> endpointsForSink(VideoSink sink) {
        return sink.getEndpoints().stream()
                .filter(ep -> ep.isOnline())
                .map(ep -> ep.getId())
                .collect(Collectors.toList());
    }

    public Map<String, List<GraphEdge>> getAdjacency() {
        return adjacency;
    }
}
