package com.or.sdvoe.discovery;

import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SdvoeDevice;

import java.util.ArrayList;
import java.util.List;

/** 基于本地配置清单的发现（手术室交付最常用）。 */
public final class InventoryFileDiscovery implements SdvoeDeviceDiscovery {

    private final List<SdvoeDevice> allDevices;

    public InventoryFileDiscovery(List<SdvoeDevice> allDevices) {
        this.allDevices = List.copyOf(allDevices);
    }

    @Override
    public String sourceName() {
        return "inventory-file";
    }

    @Override
    public List<SdvoeDevice> discover(OperatingRoom room) {
        List<SdvoeDevice> matched = new ArrayList<>();
        for (SdvoeDevice device : allDevices) {
            if (room.getId().equals(device.getOrId())) {
                matched.add(device);
            }
        }
        return matched;
    }
}
