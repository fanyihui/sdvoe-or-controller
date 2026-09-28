package com.or.sdvoe.service;

import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.domain.SdvoeDeviceRole;
import com.or.sdvoe.discovery.SdvoeDeviceDiscovery;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 本手术室 SDVoE 设备清单应用服务。 */
public class SdvoeDeviceInventoryService {

    private final OperatingRoom operatingRoom;
    private final SdvoeDeviceDiscovery discovery;

    public SdvoeDeviceInventoryService(OperatingRoom operatingRoom, SdvoeDeviceDiscovery discovery) {
        this.operatingRoom = Objects.requireNonNull(operatingRoom);
        this.discovery = Objects.requireNonNull(discovery);
    }

    public OperatingRoom currentOr() {
        return operatingRoom;
    }

    /** 获取本手术室全部 SDVoE 设备清单。 */
    public SdvoeDeviceInventory listCurrentOrDevices() {
        List<SdvoeDevice> devices = discovery.discover(operatingRoom).stream()
                .filter(d -> operatingRoom.getId().equals(d.getOrId()))
                .sorted(Comparator
                        .comparing((SdvoeDevice d) -> d.getRole().name())
                        .thenComparing(SdvoeDevice::getName))
                .collect(Collectors.toList());
        return new SdvoeDeviceInventory(
                operatingRoom, devices, Instant.now(), discovery.sourceName());
    }

    /** 按角色过滤（Encoder/Decoder/...）。 */
    public SdvoeDeviceInventory listByRole(SdvoeDeviceRole role) {
        SdvoeDeviceInventory all = listCurrentOrDevices();
        List<SdvoeDevice> filtered = all.getDevices().stream()
                .filter(d -> d.getRole() == role)
                .collect(Collectors.toList());
        return new SdvoeDeviceInventory(
                operatingRoom, filtered, all.getDiscoveredAt(), all.getDiscoverySource());
    }

    public SdvoeDevice getById(String deviceId) {
        return listCurrentOrDevices().getDevices().stream()
                .filter(d -> d.getId().equals(deviceId))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("device not found: " + deviceId));
    }
}
