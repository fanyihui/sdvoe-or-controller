package com.or.sdvoe.workspace;

import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.FabricKind;
import com.or.sdvoe.domain.FabricStep;
import com.or.sdvoe.domain.MosaicLayout;
import com.or.sdvoe.domain.PolicyDecision;
import com.or.sdvoe.domain.RouteIntent;
import com.or.sdvoe.domain.RoutePlan;
import com.or.sdvoe.domain.RouteState;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.persistence.MosaicRepository;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.FabricOrchestrator;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多视频源拼屏：选择布局 → 绑定源到格子 → 合成输出流 → 推送到目的地。
 */
public class MosaicService {

    private static final Logger log = LoggerFactory.getLogger(MosaicService.class);

    private final String orId;
    private final ScheduleService scheduleService;
    private final SdvoeDeviceInventoryService deviceInventoryService;
    private final FabricOrchestrator orchestrator;
    private final WorkspaceRoutingService routingService;
    private final MosaicRepository mosaicRepository;
    /** caseId -> (mosaicId -> mosaic) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ActiveMosaic>> mosaicsByCase =
            new ConcurrentHashMap<>();

    public MosaicService(
            String orId,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            FabricOrchestrator orchestrator,
            WorkspaceRoutingService routingService,
            MosaicRepository mosaicRepository) {
        this.orId = Objects.requireNonNull(orId);
        this.scheduleService = Objects.requireNonNull(scheduleService);
        this.deviceInventoryService = Objects.requireNonNull(deviceInventoryService);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.routingService = Objects.requireNonNull(routingService);
        this.mosaicRepository = Objects.requireNonNull(mosaicRepository);
    }

    public List<MosaicLayout> listLayouts() {
        return MosaicLayout.presets();
    }

    public List<ActiveMosaic> listMosaics(String caseId) {
        scheduleService.getCase(caseId);
        ConcurrentHashMap<String, ActiveMosaic> map = mosaicsByCase.get(caseId);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return List.copyOf(map.values());
    }

    public ActiveMosaic getMosaic(String caseId, String mosaicId) {
        ConcurrentHashMap<String, ActiveMosaic> map = mosaicsByCase.get(caseId);
        ActiveMosaic mosaic = map == null ? null : map.get(mosaicId);
        if (mosaic == null) {
            throw new NoSuchElementException("mosaic not found: " + mosaicId);
        }
        return mosaic;
    }

    public ActiveMosaic getPushedMosaicForDestination(String caseId, String destinationId) {
        for (ActiveMosaic m : listMosaics(caseId)) {
            if (m.isPushed() && destinationId.equals(m.getDestinationId())) {
                return m;
            }
        }
        return null;
    }

    public ActiveMosaic createMosaic(
            String caseId, String layoutId, String name, List<Map<String, Object>> cellAssignments) {
        scheduleService.getCase(caseId);
        MosaicLayout layout = MosaicLayout.byId(layoutId);
        List<ActiveMosaic.Cell> cells = emptyCells(layout);
        if (cellAssignments != null && !cellAssignments.isEmpty()) {
            cells = applyAssignments(caseId, cells, cellAssignments);
        }
        String mosaicId = "mos-" + UUID.randomUUID().toString().substring(0, 8);
        ActiveMosaic mosaic = new ActiveMosaic(
                mosaicId,
                caseId,
                layout.getId(),
                layout.getName(),
                layout.getRows(),
                layout.getCols(),
                name,
                cells,
                null,
                null,
                null,
                null,
                ActiveMosaic.Status.DRAFT,
                "or-desk",
                null,
                null);
        store(mosaic, true);
        log.info("Created mosaic {} layout={} case={}", mosaicId, layoutId, caseId);
        return mosaic;
    }

    public ActiveMosaic updateCells(
            String caseId, String mosaicId, List<Map<String, Object>> cellAssignments) {
        ActiveMosaic existing = getMosaic(caseId, mosaicId);
        List<ActiveMosaic.Cell> cells = applyAssignments(caseId, existing.getCells(), cellAssignments);
        ActiveMosaic updated = existing.withCells(cells);
        // 若已推送，同步重新下发合成参数
        if (updated.isPushed()) {
            updated = pushInternal(updated, updated.getDestinationId(), updated.getOperator(), true, false);
        } else {
            store(updated, true);
        }
        return updated;
    }

    public ActiveMosaic push(
            String caseId,
            String mosaicId,
            String destinationId,
            String operator,
            boolean confirmed) {
        ActiveMosaic mosaic = getMosaic(caseId, mosaicId);
        return pushInternal(mosaic, destinationId, operator, confirmed, true);
    }

    private ActiveMosaic pushInternal(
            ActiveMosaic mosaic,
            String destinationId,
            String operator,
            boolean confirmed,
            boolean checkConflict) {
        String caseId = mosaic.getCaseId();
        scheduleService.getCase(caseId);
        ScheduleRepository repo = scheduleService.repository();

        ScheduleRepository.LogicalDestinationDef destDef = repo.getDestinations().stream()
                .filter(d -> d.id().equals(destinationId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("destination not found: " + destinationId));

        long assigned = mosaic.getCells().stream().filter(ActiveMosaic.Cell::hasSource).count();
        if (assigned == 0) {
            throw new IllegalArgumentException("mosaic has no assigned sources");
        }

        if (checkConflict && !confirmed) {
            ActiveRoute existingRoute = routingService.getRouteForDestination(caseId, destinationId);
            if (existingRoute != null) {
                throw new MosaicConflictException(destinationId, "route", existingRoute.toMap());
            }
            ActiveMosaic other = getPushedMosaicForDestination(caseId, destinationId);
            if (other != null && !other.getMosaicId().equals(mosaic.getMosaicId())) {
                throw new MosaicConflictException(destinationId, "mosaic", other.toMap());
            }
        }

        // 确认覆盖：清掉该目的地的单路路由与其它拼屏推送
        if (confirmed || routingService.getRouteForDestination(caseId, destinationId) != null) {
            try {
                routingService.clearRoute(caseId, destinationId);
            } catch (Exception ignored) {
                // no-op
            }
        }
        for (ActiveMosaic other : listMosaics(caseId)) {
            if (other.isPushed()
                    && destinationId.equals(other.getDestinationId())
                    && !other.getMosaicId().equals(mosaic.getMosaicId())) {
                ActiveMosaic cleared = other.clearedPush();
                if (other.getRouteId() != null) {
                    orchestrator.release(other.getRouteId());
                }
                store(cleared, true);
            }
        }
        // 若本拼屏已推到其它目的地，先释放旧路由
        if (mosaic.isPushed()
                && mosaic.getDestinationId() != null
                && !mosaic.getDestinationId().equals(destinationId)
                && mosaic.getRouteId() != null) {
            orchestrator.release(mosaic.getRouteId());
        }

        SdvoeDeviceInventory inventory = deviceInventoryService.listCurrentOrDevices();
        Map<String, SdvoeDevice> byId = new LinkedHashMap<>();
        for (SdvoeDevice d : inventory.getDevices()) {
            byId.put(d.getId(), d);
        }

        List<ActiveMosaic.Cell> resolvedCells = resolveCellStreams(mosaic.getCells(), repo, byId);
        SdvoeDevice dec = byId.get(destDef.deviceId());
        if (dec == null) {
            throw new IllegalStateException("destination device offline/missing: " + destDef.deviceId());
        }
        if (dec.getStatus() == DeviceOnlineStatus.OFFLINE) {
            throw new IllegalStateException("destination device OFFLINE: " + dec.getId());
        }

        String outputStreamId = "mosaic-" + mosaic.getMosaicId();
        List<FabricStep> steps = buildMosaicSteps(resolvedCells, dec, outputStreamId, mosaic);

        RouteIntent intent = new RouteIntent(
                        "mosaic:" + mosaic.getMosaicId(),
                        destDef.id(),
                        operator == null ? "or-desk" : operator)
                .sceneId(caseId);
        PolicyDecision decision = new PolicyDecision(
                List.of(FabricKind.SDVOE),
                List.of(),
                new com.or.sdvoe.domain.PolicyConstraints(),
                "mosaic compose " + mosaic.getLayoutId(),
                50);
        RoutePlan plan = new RoutePlan(intent, steps, 50, List.of(FabricKind.SDVOE), decision);

        // 释放本拼屏旧路由（同目的地重推）
        if (mosaic.getRouteId() != null) {
            orchestrator.release(mosaic.getRouteId());
        }

        RouteState state = orchestrator.execute(plan, "steal_with_confirm", true);
        ActiveMosaic pushed = mosaic
                .withCells(resolvedCells)
                .withPush(
                        destDef.id(),
                        destDef.name(),
                        outputStreamId,
                        state.getRouteId(),
                        intent.getOperator());
        store(pushed, true);
        log.info(
                "Pushed mosaic {} ({}) -> {} stream={}",
                pushed.getMosaicId(),
                pushed.getLayoutId(),
                destinationId,
                outputStreamId);
        return pushed;
    }

    public void stopPush(String caseId, String mosaicId) {
        ActiveMosaic mosaic = getMosaic(caseId, mosaicId);
        if (mosaic.getRouteId() != null) {
            orchestrator.release(mosaic.getRouteId());
        }
        ActiveMosaic cleared = mosaic.clearedPush();
        store(cleared, true);
        log.info("Stopped mosaic push {}", mosaicId);
    }

    public void deleteMosaic(String caseId, String mosaicId) {
        ActiveMosaic mosaic = getMosaic(caseId, mosaicId);
        if (mosaic.getRouteId() != null) {
            orchestrator.release(mosaic.getRouteId());
        }
        ConcurrentHashMap<String, ActiveMosaic> map = mosaicsByCase.get(caseId);
        if (map != null) {
            map.remove(mosaicId);
        }
        mosaicRepository.delete(orId, mosaicId);
        log.info("Deleted mosaic {}", mosaicId);
    }

    /** 启动时从库恢复拼屏并重新推送已激活的。 */
    public int[] restorePersistedMosaics() {
        List<ActiveMosaic> persisted = mosaicRepository.findByOr(orId);
        int ok = 0;
        int fail = 0;
        for (ActiveMosaic saved : persisted) {
            mosaicsByCase
                    .computeIfAbsent(saved.getCaseId(), k -> new ConcurrentHashMap<>())
                    .put(saved.getMosaicId(), saved);
            if (!saved.isPushed()) {
                ok++;
                continue;
            }
            try {
                pushInternal(
                        saved,
                        saved.getDestinationId(),
                        saved.getOperator() == null ? "startup-restore" : saved.getOperator(),
                        true,
                        false);
                ok++;
                log.info(
                        "Restored mosaic {} -> {} (case={})",
                        saved.getMosaicId(),
                        saved.getDestinationId(),
                        saved.getCaseId());
            } catch (Exception e) {
                fail++;
                log.warn(
                        "Failed to re-apply mosaic {} -> {}: {}",
                        saved.getMosaicId(),
                        saved.getDestinationId(),
                        e.getMessage());
            }
        }
        log.info(
                "Mosaic restore finished for {}: ok={}, failed={}, total={}",
                orId,
                ok,
                fail,
                persisted.size());
        return new int[] {ok, fail};
    }

    private void store(ActiveMosaic mosaic, boolean persist) {
        mosaicsByCase
                .computeIfAbsent(mosaic.getCaseId(), k -> new ConcurrentHashMap<>())
                .put(mosaic.getMosaicId(), mosaic);
        if (persist) {
            mosaicRepository.upsert(orId, mosaic);
        }
    }

    private static List<ActiveMosaic.Cell> emptyCells(MosaicLayout layout) {
        List<ActiveMosaic.Cell> cells = new ArrayList<>();
        for (MosaicLayout.CellTemplate t : layout.getCells()) {
            cells.add(new ActiveMosaic.Cell(
                    t.index(), t.row(), t.col(), t.rowSpan(), t.colSpan(), null, null, null, null));
        }
        return cells;
    }

    private List<ActiveMosaic.Cell> applyAssignments(
            String caseId, List<ActiveMosaic.Cell> base, List<Map<String, Object>> assignments) {
        scheduleService.getCase(caseId);
        ScheduleRepository repo = scheduleService.repository();
        Map<Integer, ActiveMosaic.Cell> byIndex = new LinkedHashMap<>();
        for (ActiveMosaic.Cell c : base) {
            byIndex.put(c.index(), c);
        }
        for (Map<String, Object> a : assignments) {
            if (a == null) {
                continue;
            }
            Object idxObj = a.get("index");
            if (idxObj == null) {
                continue;
            }
            int index = idxObj instanceof Number n ? n.intValue() : Integer.parseInt(idxObj.toString());
            ActiveMosaic.Cell cell = byIndex.get(index);
            if (cell == null) {
                throw new IllegalArgumentException("cell index out of range: " + index);
            }
            Object sourceIdObj = a.get("sourceId");
            if (sourceIdObj == null || sourceIdObj.toString().isBlank()) {
                byIndex.put(index, cell.withoutSource());
                continue;
            }
            String sourceId = sourceIdObj.toString();
            ScheduleRepository.LogicalSourceDef sourceDef = repo.getSources().stream()
                    .filter(s -> s.id().equals(sourceId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("source not found: " + sourceId));
            byIndex.put(
                    index,
                    cell.withSource(sourceDef.id(), sourceDef.name(), sourceDef.deviceId(), null));
        }
        return new ArrayList<>(byIndex.values());
    }

    private List<ActiveMosaic.Cell> resolveCellStreams(
            List<ActiveMosaic.Cell> cells,
            ScheduleRepository repo,
            Map<String, SdvoeDevice> byId) {
        List<ActiveMosaic.Cell> resolved = new ArrayList<>();
        for (ActiveMosaic.Cell cell : cells) {
            if (!cell.hasSource()) {
                resolved.add(cell);
                continue;
            }
            ScheduleRepository.LogicalSourceDef sourceDef = repo.getSources().stream()
                    .filter(s -> s.id().equals(cell.sourceId()))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("source not found: " + cell.sourceId()));
            SdvoeDevice enc = byId.get(sourceDef.deviceId());
            if (enc == null) {
                throw new IllegalStateException("source device offline/missing: " + sourceDef.deviceId());
            }
            if (enc.getStatus() == DeviceOnlineStatus.OFFLINE) {
                throw new IllegalStateException("source device OFFLINE: " + enc.getId());
            }
            String streamId = enc.getStreamId() != null && !enc.getStreamId().isBlank()
                    ? enc.getStreamId()
                    : "stream-" + sourceDef.id();
            resolved.add(cell.withSource(sourceDef.id(), sourceDef.name(), enc.getId(), streamId));
        }
        return resolved;
    }

    private List<FabricStep> buildMosaicSteps(
            List<ActiveMosaic.Cell> cells,
            SdvoeDevice decoder,
            String outputStreamId,
            ActiveMosaic mosaic) {
        List<FabricStep> steps = new ArrayList<>();
        List<Map<String, Object>> mosaicCells = new ArrayList<>();

        for (ActiveMosaic.Cell cell : cells) {
            if (!cell.hasSource()) {
                continue;
            }
            Map<String, Object> setStream = new LinkedHashMap<>();
            setStream.put("encoder_id", cell.deviceId());
            setStream.put("stream_id", cell.streamId());
            setStream.put("mode", "multicast");
            steps.add(new FabricStep(FabricKind.SDVOE, "set_stream", setStream));

            Map<String, Object> cellParam = new LinkedHashMap<>();
            cellParam.put("index", cell.index());
            cellParam.put("row", cell.row());
            cellParam.put("col", cell.col());
            cellParam.put("row_span", cell.rowSpan());
            cellParam.put("col_span", cell.colSpan());
            cellParam.put("encoder_id", cell.deviceId());
            cellParam.put("stream_id", cell.streamId());
            cellParam.put("source_id", cell.sourceId());
            mosaicCells.add(cellParam);
        }

        Map<String, Object> configure = new LinkedHashMap<>();
        configure.put("mosaic_id", mosaic.getMosaicId());
        configure.put("layout_id", mosaic.getLayoutId());
        configure.put("rows", mosaic.getRows());
        configure.put("cols", mosaic.getCols());
        configure.put("output_stream_id", outputStreamId);
        configure.put("cells", mosaicCells);
        steps.add(new FabricStep(FabricKind.SDVOE, "configure_mosaic", configure)
                .compensationAction("clear_mosaic")
                .compensationParams(Map.of(
                        "mosaic_id", mosaic.getMosaicId(),
                        "output_stream_id", outputStreamId)));

        Map<String, Object> subscribe = new LinkedHashMap<>();
        subscribe.put("decoder_id", decoder.getId());
        subscribe.put("stream_id", outputStreamId);
        subscribe.put("mosaic_id", mosaic.getMosaicId());
        steps.add(new FabricStep(FabricKind.SDVOE, "subscribe", subscribe));
        return steps;
    }
}
