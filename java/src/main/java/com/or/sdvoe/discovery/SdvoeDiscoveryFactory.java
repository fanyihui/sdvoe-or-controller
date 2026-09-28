package com.or.sdvoe.discovery;

import com.or.sdvoe.config.OrControllerConfig;

/** 按配置创建发现实现。 */
public final class SdvoeDiscoveryFactory {

    private SdvoeDiscoveryFactory() {
    }

    public static SdvoeDeviceDiscovery create(OrControllerConfig config) {
        return switch (config.getDiscoveryMode()) {
            case "mock" -> new MockSdvoeDiscovery();
            case "http", "manager" -> {
                if (config.getManagerBaseUrl() == null || config.getManagerBaseUrl().isBlank()) {
                    throw new IllegalStateException(
                            "sdvoe.discovery.manager_base_url is required when mode=http");
                }
                yield new HttpSdvoeManagerDiscovery(config.getManagerBaseUrl());
            }
            case "inventory", "file" -> new InventoryFileDiscovery(config.getInventoryDevices());
            default -> throw new IllegalStateException(
                    "Unknown sdvoe.discovery.mode: " + config.getDiscoveryMode()
                            + " (supported: inventory|mock|http)");
        };
    }
}
