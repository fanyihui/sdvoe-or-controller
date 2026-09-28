package com.or.sdvoe.adapter;

import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.PhysicalEndpoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Controls Matrix <-> SDVoE media converters / glue ports. */
public class BridgeAdapter implements FabricAdapter {

    private final Set<String> enabled = new HashSet<>();

    @Override
    public FabricKind kind() {
        return FabricKind.BRIDGE;
    }

    @Override
    public List<PhysicalEndpoint> discover() {
        return List.of();
    }

    @Override
    public FabricHealth getHealth() {
        return new FabricHealth(kind(), true);
    }

    @Override
    public ApplyResult applySteps(List<FabricStep> steps) {
        List<FabricStep> applied = new ArrayList<>();
        for (FabricStep step : steps) {
            if (step.getFabric() != FabricKind.BRIDGE) {
                continue;
            }
            String bridgeId = String.valueOf(step.getParams().getOrDefault("bridge_id", "default"));
            if ("enable_bridge".equals(step.getAction())) {
                enabled.add(bridgeId);
                applied.add(step);
            } else if ("disable_bridge".equals(step.getAction())) {
                enabled.remove(bridgeId);
                applied.add(step);
            }
        }
        return ApplyResult.success("bridge ok", applied);
    }

    @Override
    public void release(String routeId) {
        // no-op
    }
}
