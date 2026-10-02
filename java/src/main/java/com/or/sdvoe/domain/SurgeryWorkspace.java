package com.or.sdvoe.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 患者手术工作空间聚合视图。 */
public final class SurgeryWorkspace {
    private final SurgeryCase surgeryCase;
    private final OperatingRoom operatingRoom;
    private final List<WorkspaceSource> sources;
    private final List<WorkspaceDestination> destinations;
    private final List<Map<String, Object>> activeRoutes;
    private final List<Map<String, Object>> activeMosaics;
    private final List<Map<String, Object>> mosaicLayouts;
    private final Map<String, Object> activeRecording;
    private final Instant generatedAt;

    public SurgeryWorkspace(
            SurgeryCase surgeryCase,
            OperatingRoom operatingRoom,
            List<WorkspaceSource> sources,
            List<WorkspaceDestination> destinations) {
        this(surgeryCase, operatingRoom, sources, destinations, List.of(), List.of(), List.of(), null);
    }

    public SurgeryWorkspace(
            SurgeryCase surgeryCase,
            OperatingRoom operatingRoom,
            List<WorkspaceSource> sources,
            List<WorkspaceDestination> destinations,
            List<Map<String, Object>> activeRoutes) {
        this(surgeryCase, operatingRoom, sources, destinations, activeRoutes, List.of(), List.of(), null);
    }

    public SurgeryWorkspace(
            SurgeryCase surgeryCase,
            OperatingRoom operatingRoom,
            List<WorkspaceSource> sources,
            List<WorkspaceDestination> destinations,
            List<Map<String, Object>> activeRoutes,
            List<Map<String, Object>> activeMosaics,
            List<Map<String, Object>> mosaicLayouts) {
        this(surgeryCase, operatingRoom, sources, destinations, activeRoutes, activeMosaics, mosaicLayouts, null);
    }

    public SurgeryWorkspace(
            SurgeryCase surgeryCase,
            OperatingRoom operatingRoom,
            List<WorkspaceSource> sources,
            List<WorkspaceDestination> destinations,
            List<Map<String, Object>> activeRoutes,
            List<Map<String, Object>> activeMosaics,
            List<Map<String, Object>> mosaicLayouts,
            Map<String, Object> activeRecording) {
        this.surgeryCase = surgeryCase;
        this.operatingRoom = operatingRoom;
        this.sources = List.copyOf(sources);
        this.destinations = List.copyOf(destinations);
        this.activeRoutes = List.copyOf(activeRoutes);
        this.activeMosaics = List.copyOf(activeMosaics);
        this.mosaicLayouts = List.copyOf(mosaicLayouts);
        this.activeRecording = activeRecording;
        this.generatedAt = Instant.now();
    }

    public SurgeryCase getSurgeryCase() {
        return surgeryCase;
    }

    public List<WorkspaceSource> getSources() {
        return sources;
    }

    public List<WorkspaceDestination> getDestinations() {
        return destinations;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> or = new LinkedHashMap<>();
        or.put("id", operatingRoom.getId());
        or.put("name", operatingRoom.getName());
        or.put("building", operatingRoom.getBuilding());
        or.put("floor", operatingRoom.getFloor());
        m.put("operatingRoom", or);
        m.put("case", surgeryCase.toDetailMap());
        m.put("patient", surgeryCase.getPatient().toMap());
        List<Map<String, Object>> srcMaps = new ArrayList<>();
        String recordingSourceId = activeRecording == null ? null : String.valueOf(activeRecording.get("sourceId"));
        boolean recordingBusy = activeRecording != null
                && Boolean.TRUE.equals(activeRecording.get("active"));
        for (WorkspaceSource s : sources) {
            boolean isRec = recordingSourceId != null && recordingSourceId.equals(s.getId());
            srcMaps.add(s.toMap(isRec, recordingBusy && !isRec));
        }
        List<Map<String, Object>> dstMaps = new ArrayList<>();
        for (WorkspaceDestination d : destinations) {
            dstMaps.add(d.toMap());
        }
        m.put("sources", srcMaps);
        m.put("destinations", dstMaps);
        m.put("activeRoutes", activeRoutes);
        m.put("activeMosaics", activeMosaics);
        m.put("mosaicLayouts", mosaicLayouts);
        m.put("activeRecording", activeRecording);
        m.put("generatedAt", generatedAt.toString());
        return m;
    }
}
