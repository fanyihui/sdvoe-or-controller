package com.or.sdvoe.adapter;

import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.PhysicalEndpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SDVoE encoder/decoder control skeleton. */
public class SDVoEAdapter implements FabricAdapter {

    private final Object managerClient;
    private final Map<String, Map<String, Object>> subscriptions = new HashMap<>();

    public SDVoEAdapter(Object managerClient) {
        this.managerClient = managerClient;
    }

    @Override
    public FabricKind kind() {
        return FabricKind.SDVOE;
    }

    @Override
    public List<PhysicalEndpoint> discover() {
        return List.of();
    }

    @Override
    public FabricHealth getHealth() {
        return new FabricHealth(kind(), true, Map.of("manager", managerClient == null ? "stub" : "ok"));
    }

    @Override
    public ApplyResult applySteps(List<FabricStep> steps) {
        List<FabricStep> applied = new ArrayList<>();
        for (FabricStep step : steps) {
            if (step.getFabric() != FabricKind.SDVOE) {
                continue;
            }
            switch (step.getAction()) {
                case "set_stream" -> {
                    String enc = String.valueOf(step.getParams().get("encoder_id"));
                    subscriptions.put(enc, Map.copyOf(step.getParams()));
                    applied.add(step);
                }
                case "subscribe" -> {
                    String dec = String.valueOf(step.getParams().get("decoder_id"));
                    subscriptions.put(dec, Map.copyOf(step.getParams()));
                    applied.add(step);
                }
                case "configure_mosaic" -> {
                    String mosaicId = String.valueOf(step.getParams().get("mosaic_id"));
                    subscriptions.put("mosaic:" + mosaicId, Map.copyOf(step.getParams()));
                    applied.add(step);
                }
                case "clear_mosaic" -> {
                    Object mosaicId = step.getParams().get("mosaic_id");
                    if (mosaicId != null) {
                        subscriptions.remove("mosaic:" + mosaicId);
                    }
                    applied.add(step);
                }
                case "set_genlock" -> applied.add(step);
                default -> {
                    return ApplyResult.failure("unsupported sdvoe action: " + step.getAction());
                }
            }
        }
        return ApplyResult.success("sdvoe ok", applied);
    }

    @Override
    public void release(String routeId) {
        subscriptions.remove(routeId);
    }
}
