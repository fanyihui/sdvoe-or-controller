package com.or.sdvoe.adapter;

import com.or.sdvoe.domain.FabricKind;

import java.util.Map;

public record FabricHealth(FabricKind fabric, boolean online, Map<String, Object> detail) {
    public FabricHealth(FabricKind fabric, boolean online) {
        this(fabric, online, Map.of());
    }
}
