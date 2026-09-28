package com.or.sdvoe.adapter;

import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.PhysicalEndpoint;

import java.util.List;
import java.util.function.Consumer;

public interface FabricAdapter {
    FabricKind kind();

    List<PhysicalEndpoint> discover();

    FabricHealth getHealth();

    ApplyResult applySteps(List<FabricStep> steps);

    void release(String routeId);

    default void subscribeEvents(Consumer<Object> handler) {
        // optional
    }
}
