package com.or.sdvoe.service;

import com.or.sdvoe.adapter.ApplyResult;
import com.or.sdvoe.adapter.FabricAdapter;
import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.LockMode;
import com.or.sdvoe.domain.RoutePlan;
import com.or.sdvoe.domain.RouteState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Transactional multi-fabric route apply with compensation. */
public class FabricOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(FabricOrchestrator.class);

    private final Map<FabricKind, FabricAdapter> adapters;
    private final Map<String, RouteState> routes = new HashMap<>();
    private final Map<String, String> sinkIndex = new HashMap<>();
    private final AtomicInteger idSeq = new AtomicInteger(1);

    public FabricOrchestrator(Map<FabricKind, FabricAdapter> adapters) {
        this.adapters = adapters;
    }

    public RouteState execute(RoutePlan plan, String conflictPolicy, boolean confirmed) {
        String sinkId = plan.getIntent().getSinkId();
        String existing = sinkIndex.get(sinkId);
        if (existing != null && "reject".equals(conflictPolicy)) {
            throw new IllegalStateException("sink " + sinkId + " busy by route " + existing);
        }
        if (existing != null && "steal_with_confirm".equals(conflictPolicy) && !confirmed) {
            throw new SecurityException("sink " + sinkId + " occupied by " + existing + "; confirm to steal");
        }

        String routeId = "rt-" + idSeq.getAndIncrement();
        List<AppliedStep> applied = new ArrayList<>();

        try {
            for (FabricStep step : plan.getSteps()) {
                FabricAdapter adapter = adapters.get(step.getFabric());
                if (adapter == null && step.getFabric() == FabricKind.BRIDGE) {
                    continue;
                }
                if (adapter == null) {
                    throw new IllegalStateException("no adapter for fabric " + step.getFabric());
                }
                ApplyResult result = adapter.applySteps(List.of(step));
                if (!result.ok()) {
                    throw new IllegalStateException(result.message());
                }
                applied.add(new AppliedStep(adapter, step));
            }

            if (existing != null) {
                release(existing);
            }

            LockMode lock = plan.getIntent().getRequestLock() == LockMode.NONE
                    ? plan.getDecision().getConstraints().getLockMode()
                    : plan.getIntent().getRequestLock();

            RouteState state = new RouteState(routeId, plan.getIntent().getSourceId(), sinkId, plan)
                    .lockMode(lock);
            routes.put(routeId, state);
            sinkIndex.put(sinkId, routeId);

            log.info(
                    "route applied {} {} -> {} via {} ({})",
                    routeId,
                    state.getSourceId(),
                    state.getSinkId(),
                    plan.getFabricsUsed(),
                    plan.getDecision().getReason());
            return state;
        } catch (RuntimeException ex) {
            compensate(applied);
            throw ex;
        }
    }

    public void release(String routeId) {
        RouteState state = routes.remove(routeId);
        if (state == null) {
            return;
        }
        sinkIndex.remove(state.getSinkId());
        for (FabricKind fabric : state.getPlan().getFabricsUsed()) {
            FabricAdapter adapter = adapters.get(fabric);
            if (adapter != null) {
                adapter.release(routeId);
            }
        }
    }

    public RouteState getRouteForSink(String sinkId) {
        String rid = sinkIndex.get(sinkId);
        return rid == null ? null : routes.get(rid);
    }

    /** Visible for RouteService genlock lookup. */
    public Map<String, RouteState> getRoutes() {
        return Collections.unmodifiableMap(routes);
    }

    private void compensate(List<AppliedStep> applied) {
        List<AppliedStep> reversed = new ArrayList<>(applied);
        Collections.reverse(reversed);
        for (AppliedStep item : reversed) {
            if (item.step.getCompensationAction() == null) {
                continue;
            }
            FabricStep comp = new FabricStep(
                            item.step.getFabric(),
                            item.step.getCompensationAction(),
                            item.step.getCompensationParams() == null
                                    ? Map.of()
                                    : item.step.getCompensationParams());
            try {
                item.adapter.applySteps(List.of(comp));
            } catch (Exception e) {
                log.error("compensation failed: {}", e.getMessage());
            }
        }
    }

    private record AppliedStep(FabricAdapter adapter, FabricStep step) {
    }
}
