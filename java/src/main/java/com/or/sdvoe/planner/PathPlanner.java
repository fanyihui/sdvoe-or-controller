package com.or.sdvoe.planner;

import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.PolicyDecision;
import com.or.sdvoe.domain.RouteIntent;
import com.or.sdvoe.domain.RoutePlan;
import com.or.sdvoe.domain.VideoSink;
import com.or.sdvoe.domain.VideoSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra-style planner with policy constraints and fabric preference.
 */
public class PathPlanner {

    private final Map<String, Double> weights;

    public PathPlanner() {
        this(Map.of(
                "latency", 0.45,
                "bridge_hop", 0.25,
                "reliability", 0.15,
                "hop_count", 0.10,
                "fabric_preference", 0.05));
    }

    public PathPlanner(Map<String, Double> weights) {
        this.weights = new HashMap<>(weights);
    }

    public Optional<RoutePlan> plan(
            TopologyGraph graph,
            VideoSource source,
            VideoSink sink,
            RouteIntent intent,
            PolicyDecision decision) {

        List<String> starts = graph.endpointsForSource(source);
        Set<String> goals = new HashSet<>(graph.endpointsForSink(sink));
        if (starts.isEmpty() || goals.isEmpty()) {
            return Optional.empty();
        }

        List<FabricKind> fabricOrder = new ArrayList<>(decision.getPreferredFabrics());
        for (FabricKind f : decision.getFallbackFabrics()) {
            if (!fabricOrder.contains(f)) {
                fabricOrder.add(f);
            }
        }

        SearchResult best = null;
        for (String start : starts) {
            Optional<SearchResult> result = dijkstra(graph, start, goals, decision, fabricOrder);
            if (result.isPresent()) {
                SearchResult current = result.get();
                if (best == null || current.cost < best.cost) {
                    best = current;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }

        List<FabricStep> steps = edgesToSteps(best.edges);
        List<FabricKind> fabrics = new ArrayList<>();
        for (GraphEdge edge : best.edges) {
            if (edge.getFabric() != FabricKind.BRIDGE && !fabrics.contains(edge.getFabric())) {
                fabrics.add(edge.getFabric());
            }
        }
        int latency = best.edges.stream().mapToInt(GraphEdge::getLatencyMs).sum();
        return Optional.of(new RoutePlan(intent, steps, latency, fabrics, decision).pathNodeIds(best.path));
    }

    private Optional<SearchResult> dijkstra(
            TopologyGraph graph,
            String start,
            Set<String> goals,
            PolicyDecision decision,
            List<FabricKind> fabricOrder) {

        PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingDouble(s -> s.cost));
        pq.add(new State(0.0, 0, 0, 0, start, List.of(start), List.of()));
        Map<String, Double> visited = new HashMap<>();

        while (!pq.isEmpty()) {
            State state = pq.poll();
            if (visited.containsKey(state.node) && visited.get(state.node) <= state.cost) {
                continue;
            }
            visited.put(state.node, state.cost);

            if (goals.contains(state.node)) {
                if (state.latency <= decision.getConstraints().getMaxLatencyMs()
                        && state.bridges <= decision.getConstraints().getMaxBridgeHops()) {
                    return Optional.of(new SearchResult(state.cost, state.path, state.edges));
                }
                continue;
            }

            for (GraphEdge edge : graph.getAdjacency().getOrDefault(state.node, List.of())) {
                int newBridges = state.bridges + (edge.isBridge() ? 1 : 0);
                if (newBridges > decision.getConstraints().getMaxBridgeHops()) {
                    continue;
                }
                int newLatency = state.latency + edge.getLatencyMs();
                if (newLatency > decision.getConstraints().getMaxLatencyMs()) {
                    continue;
                }
                if (decision.getConstraints().isRequireLossless()
                        && Boolean.TRUE.equals(edge.getMeta().get("lossy"))) {
                    continue;
                }

                double prefPenalty = fabricPreferencePenalty(edge.getFabric(), fabricOrder);
                double stepCost =
                        weights.get("latency") * edge.getLatencyMs()
                                + weights.get("bridge_hop") * (edge.isBridge() ? 20.0 : 0.0)
                                + weights.get("reliability") * (1.0 - edge.getReliability()) * 50.0
                                + weights.get("hop_count") * 5.0
                                + weights.get("fabric_preference") * prefPenalty;

                List<String> newPath = new ArrayList<>(state.path);
                newPath.add(edge.getDst());
                List<GraphEdge> newEdges = new ArrayList<>(state.edges);
                newEdges.add(edge);
                pq.add(new State(
                        state.cost + stepCost,
                        newLatency,
                        newBridges,
                        state.hops + 1,
                        edge.getDst(),
                        newPath,
                        newEdges));
            }
        }
        return Optional.empty();
    }

    private static double fabricPreferencePenalty(FabricKind fabric, List<FabricKind> fabricOrder) {
        if (fabric == FabricKind.BRIDGE) {
            return 15.0;
        }
        int idx = fabricOrder.indexOf(fabric);
        return idx >= 0 ? idx * 10.0 : 30.0;
    }

    @SuppressWarnings("unchecked")
    private static List<FabricStep> edgesToSteps(List<GraphEdge> edges) {
        List<FabricStep> steps = new ArrayList<>();
        for (GraphEdge edge : edges) {
            Map<String, Object> meta = edge.getMeta();
            if (edge.getFabric() == FabricKind.MATRIX && "switch".equals(meta.get("action"))) {
                Map<String, Object> params = new HashMap<>();
                params.put("input", meta.get("input"));
                params.put("output", meta.get("output"));
                FabricStep step = new FabricStep(FabricKind.MATRIX, "switch_crosspoint", params)
                        .compensationAction("switch_crosspoint");
                if (meta.get("compensation") instanceof Map<?, ?> comp) {
                    step.compensationParams((Map<String, Object>) comp);
                }
                steps.add(step);
            } else if (edge.getFabric() == FabricKind.SDVOE) {
                String action = ObjectsToString(meta.get("action"), "subscribe");
                Map<String, Object> params = meta.get("params") instanceof Map<?, ?> p
                        ? new HashMap<>((Map<String, Object>) p)
                        : new HashMap<>();
                steps.add(new FabricStep(FabricKind.SDVOE, action, params));
            } else if (edge.isBridge() || edge.getFabric() == FabricKind.BRIDGE) {
                steps.add(new FabricStep(FabricKind.BRIDGE, "enable_bridge", new HashMap<>(meta)));
            }
        }
        return steps;
    }

    private static String ObjectsToString(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    private record State(
            double cost,
            int latency,
            int bridges,
            int hops,
            String node,
            List<String> path,
            List<GraphEdge> edges) {
    }

    private record SearchResult(double cost, List<String> path, List<GraphEdge> edges) {
    }
}
