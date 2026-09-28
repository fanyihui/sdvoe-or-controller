package com.or.sdvoe.service;

import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.RouteIntent;
import com.or.sdvoe.domain.RoutePlan;
import com.or.sdvoe.domain.RouteState;
import com.or.sdvoe.domain.VideoSink;
import com.or.sdvoe.domain.VideoSource;
import com.or.sdvoe.planner.PathPlanner;
import com.or.sdvoe.planner.TopologyGraph;
import com.or.sdvoe.policy.PolicyEngine;

import java.util.ArrayList;
import java.util.Optional;

/** Application service: intent -> policy -> plan -> orchestrate. */
public class RouteService {

    private final Inventory inventory;
    private final TopologyGraph graph;
    private final PolicyEngine policyEngine;
    private final PathPlanner planner;
    private final FabricOrchestrator orchestrator;
    private final String conflictPolicy;

    public RouteService(
            Inventory inventory,
            TopologyGraph graph,
            PolicyEngine policyEngine,
            PathPlanner planner,
            FabricOrchestrator orchestrator,
            String conflictPolicy) {
        this.inventory = inventory;
        this.graph = graph;
        this.policyEngine = policyEngine;
        this.planner = planner;
        this.orchestrator = orchestrator;
        this.conflictPolicy = conflictPolicy;
    }

    public RouteState route(RouteIntent intent) {
        return route(intent, false, 1, false);
    }

    public RouteState route(RouteIntent intent, boolean confirmed, int fanoutCount, boolean crossRoom) {
        VideoSource source = inventory.getSource(intent.getSourceId());
        VideoSink sink = inventory.getSink(intent.getSinkId());

        FabricKind activeEndoFabric = activeFabricForTypePrefix("endoscope");
        var decision = policyEngine.decide(source, sink, fanoutCount, crossRoom, activeEndoFabric);

        Optional<RoutePlan> plan = planner.plan(graph, source, sink, intent, decision);
        if (plan.isEmpty() && !decision.getFallbackFabrics().isEmpty()) {
            decision.setPreferredFabrics(new ArrayList<>(decision.getFallbackFabrics()));
            plan = planner.plan(graph, source, sink, intent, decision);
        }
        if (plan.isEmpty()) {
            throw new IllegalStateException(
                    "no feasible path for " + intent.getSourceId() + " -> " + intent.getSinkId()
                            + "; policy=" + decision.getReason());
        }
        return orchestrator.execute(plan.get(), conflictPolicy, confirmed);
    }

    private FabricKind activeFabricForTypePrefix(String sourceIdPrefix) {
        for (RouteState state : orchestrator.getRoutes().values()) {
            if (state.isActive() && state.getSourceId().startsWith(sourceIdPrefix)) {
                if (!state.getPlan().getFabricsUsed().isEmpty()) {
                    return state.getPlan().getFabricsUsed().get(0);
                }
            }
        }
        return null;
    }
}
