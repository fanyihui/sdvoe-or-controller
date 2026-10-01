package com.or.sdvoe.workspace;

import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.OperatingRoom;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.domain.SurgeryCase;
import com.or.sdvoe.domain.SurgeryWorkspace;
import com.or.sdvoe.domain.WorkspaceDestination;
import com.or.sdvoe.domain.WorkspaceSource;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 组装患者手术工作空间：病例 + 源 + 目的地（带设备实时状态）。 */
public class SurgeryWorkspaceService {

    private final OperatingRoom operatingRoom;
    private final ScheduleService scheduleService;
    private final SdvoeDeviceInventoryService deviceInventoryService;

    public SurgeryWorkspaceService(
            OperatingRoom operatingRoom,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService) {
        this.operatingRoom = Objects.requireNonNull(operatingRoom);
        this.scheduleService = Objects.requireNonNull(scheduleService);
        this.deviceInventoryService = Objects.requireNonNull(deviceInventoryService);
    }

    public SurgeryWorkspace getWorkspace(String caseId) {
        SurgeryCase surgeryCase = scheduleService.getCase(caseId);
        SdvoeDeviceInventory inventory = deviceInventoryService.listCurrentOrDevices();
        Map<String, SdvoeDevice> byId = new HashMap<>();
        for (SdvoeDevice d : inventory.getDevices()) {
            byId.put(d.getId(), d);
        }

        ScheduleRepository repo = scheduleService.repository();
        List<WorkspaceSource> sources = new ArrayList<>();
        for (ScheduleRepository.LogicalSourceDef def : repo.getSources()) {
            SdvoeDevice device = byId.get(def.deviceId());
            sources.add(new WorkspaceSource(
                    def.id(),
                    def.name(),
                    def.sourceType(),
                    def.deviceId(),
                    def.critical(),
                    device == null ? DeviceOnlineStatus.UNKNOWN : device.getStatus(),
                    device != null && device.isSignalPresent(),
                    device == null ? null : device.getStreamId(),
                    device == null ? null : device.getIpAddress(),
                    device == null ? null : device.getLocation()));
        }

        List<WorkspaceDestination> destinations = new ArrayList<>();
        for (ScheduleRepository.LogicalDestinationDef def : repo.getDestinations()) {
            SdvoeDevice device = byId.get(def.deviceId());
            destinations.add(new WorkspaceDestination(
                    def.id(),
                    def.name(),
                    def.role(),
                    def.deviceId(),
                    def.ultraLowLatency(),
                    device == null ? DeviceOnlineStatus.UNKNOWN : device.getStatus(),
                    device != null && device.isSignalPresent(),
                    device == null ? null : device.getStreamId(),
                    device == null ? null : device.getIpAddress(),
                    device == null ? null : device.getLocation()));
        }

        return new SurgeryWorkspace(surgeryCase, operatingRoom, sources, destinations);
    }
}
