package com.or.sdvoe.adapter;

import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.PhysicalEndpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Vendor-agnostic matrix adapter skeleton. */
public class MatrixAdapter implements FabricAdapter {

    private final Object transport;
    private final Map<String, Map<String, Object>> routes = new HashMap<>();

    public MatrixAdapter(Object transport) {
        this.transport = transport;
    }

    @Override
    public FabricKind kind() {
        return FabricKind.MATRIX;
    }

    @Override
    public List<PhysicalEndpoint> discover() {
        return List.of();
    }

    @Override
    public FabricHealth getHealth() {
        return new FabricHealth(kind(), true, Map.of("transport", transport == null ? "stub" : "ok"));
    }

    @Override
    public ApplyResult applySteps(List<FabricStep> steps) {
        List<FabricStep> applied = new ArrayList<>();
        for (FabricStep step : steps) {
            if (step.getFabric() != FabricKind.MATRIX) {
                continue;
            }
            if (!"switch_crosspoint".equals(step.getAction())) {
                return ApplyResult.failure("unsupported matrix action: " + step.getAction());
            }
            Object input = step.getParams().get("input");
            Object output = step.getParams().get("output");
            routes.put(String.valueOf(output), Map.of("input", input));
            applied.add(step);
        }
        return ApplyResult.success("matrix ok", applied);
    }

    @Override
    public void release(String routeId) {
        routes.remove(routeId);
    }
}
