package com.or.sdvoe.adapter;

import com.or.sdvoe.domain.FabricStep;

import java.util.List;

public record ApplyResult(boolean ok, String message, List<FabricStep> applied) {
    public static ApplyResult success(String message, List<FabricStep> applied) {
        return new ApplyResult(true, message, applied);
    }

    public static ApplyResult failure(String message) {
        return new ApplyResult(false, message, List.of());
    }
}
