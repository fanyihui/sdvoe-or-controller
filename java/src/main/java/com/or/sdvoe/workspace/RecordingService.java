package com.or.sdvoe.workspace;

import com.or.sdvoe.adapter.RecorderControlPort;
import com.or.sdvoe.domain.DeviceOnlineStatus;
import com.or.sdvoe.domain.SdvoeDevice;
import com.or.sdvoe.domain.SdvoeDeviceInventory;
import com.or.sdvoe.persistence.RecordingRepository;
import com.or.sdvoe.schedule.ScheduleRepository;
import com.or.sdvoe.schedule.ScheduleService;
import com.or.sdvoe.service.SdvoeDeviceInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单路软件录制（选项 B）P0：会话生命周期 + Stub Worker。
 * 不强制占用 dst-recorder 路由。
 */
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    private final String orId;
    private final ScheduleService scheduleService;
    private final SdvoeDeviceInventoryService deviceInventoryService;
    private final RecorderControlPort recorder;
    private final RecordingRepository recordingRepository;
    /** caseId -> sessionId -> recording */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ActiveRecording>> byCase =
            new ConcurrentHashMap<>();

    public RecordingService(
            String orId,
            ScheduleService scheduleService,
            SdvoeDeviceInventoryService deviceInventoryService,
            RecorderControlPort recorder,
            RecordingRepository recordingRepository) {
        this.orId = Objects.requireNonNull(orId);
        this.scheduleService = Objects.requireNonNull(scheduleService);
        this.deviceInventoryService = Objects.requireNonNull(deviceInventoryService);
        this.recorder = Objects.requireNonNull(recorder);
        this.recordingRepository = Objects.requireNonNull(recordingRepository);
    }

    public List<ActiveRecording> listRecordings(String caseId) {
        scheduleService.getCase(caseId);
        ConcurrentHashMap<String, ActiveRecording> map = byCase.get(caseId);
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        List<ActiveRecording> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparing(ActiveRecording::getStartedAt));
        return List.copyOf(list);
    }

    public ActiveRecording getActive(String caseId) {
        for (ActiveRecording r : listRecordings(caseId)) {
            if (r.isActive()) {
                return r;
            }
        }
        return null;
    }

    public ActiveRecording get(String caseId, String sessionId) {
        ConcurrentHashMap<String, ActiveRecording> map = byCase.get(caseId);
        ActiveRecording r = map == null ? null : map.get(sessionId);
        if (r == null) {
            throw new NoSuchElementException("recording not found: " + sessionId);
        }
        return r;
    }

    public ActiveRecording start(String caseId, String sourceId, String operator) {
        scheduleService.getCase(caseId);
        ActiveRecording existing = getActive(caseId);
        if (existing != null) {
            throw new RecordingConflictException(existing);
        }

        ScheduleRepository.LogicalSourceDef sourceDef = scheduleService.repository().getSources().stream()
                .filter(s -> s.id().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("source not found: " + sourceId));

        SdvoeDeviceInventory inventory = deviceInventoryService.listCurrentOrDevices();
        SdvoeDevice enc = null;
        for (SdvoeDevice d : inventory.getDevices()) {
            if (sourceDef.deviceId().equals(d.getId())) {
                enc = d;
                break;
            }
        }
        if (enc == null) {
            throw new IllegalStateException("source device offline/missing: " + sourceDef.deviceId());
        }
        if (enc.getStatus() == DeviceOnlineStatus.OFFLINE) {
            throw new IllegalStateException("source device OFFLINE: " + enc.getId());
        }
        if (!enc.isSignalPresent()) {
            throw new IllegalStateException("source has no signal: " + sourceDef.id());
        }

        String streamId = enc.getStreamId() != null && !enc.getStreamId().isBlank()
                ? enc.getStreamId()
                : "stream-" + sourceDef.id();
        String sessionId = "rec-" + UUID.randomUUID().toString().substring(0, 8);

        ActiveRecording preparing = new ActiveRecording(
                sessionId,
                caseId,
                sourceDef.id(),
                sourceDef.name(),
                enc.getId(),
                streamId,
                ActiveRecording.Mode.SOFTWARE,
                recorder.targetId(),
                ActiveRecording.Status.PREPARING,
                null,
                null,
                operator == null ? "or-desk" : operator,
                null,
                null,
                null,
                null);
        store(preparing);

        RecorderControlPort.StartResult started = recorder.start(new RecorderControlPort.StartRequest(
                sessionId, orId, caseId, sourceDef.id(), streamId, null));
        if (!started.ok()) {
            ActiveRecording failed = preparing.withFailed(started.message());
            store(failed);
            throw new IllegalStateException("recorder start failed: " + started.message());
        }

        ActiveRecording recording = new ActiveRecording(
                sessionId,
                caseId,
                sourceDef.id(),
                sourceDef.name(),
                enc.getId(),
                streamId,
                ActiveRecording.Mode.SOFTWARE,
                recorder.targetId(),
                ActiveRecording.Status.RECORDING,
                started.artifactUri(),
                null,
                preparing.getOperator(),
                null,
                preparing.getStartedAt(),
                null,
                null);
        store(recording);
        log.info(
                "Recording started {} source={} stream={} artifact={}",
                sessionId,
                sourceId,
                streamId,
                started.artifactUri());
        return recording;
    }

    public ActiveRecording stop(String caseId, String sessionId) {
        ActiveRecording current = get(caseId, sessionId);
        if (!current.isActive()) {
            return current;
        }
        ActiveRecording stopping = current.withStatus(ActiveRecording.Status.STOPPING);
        store(stopping);

        RecorderControlPort.StopResult stopped = recorder.stop(sessionId);
        ActiveRecording finalState;
        if (stopped.ok()) {
            finalState = stopping.withStopped(stopped.artifactUri(), stopped.artifactBytes());
        } else {
            finalState = stopping.withFailed(stopped.message());
        }
        store(finalState);
        log.info(
                "Recording stopped {} status={} artifact={}",
                sessionId,
                finalState.getStatus(),
                finalState.getArtifactUri());
        return finalState;
    }

    /** 换台/结束前硬停本案所有活跃录制。 */
    public List<ActiveRecording> hardStopCase(String caseId) {
        List<ActiveRecording> stopped = new ArrayList<>();
        for (ActiveRecording r : listRecordings(caseId)) {
            if (r.isActive()) {
                stopped.add(stop(caseId, r.getSessionId()));
            }
        }
        return stopped;
    }

    public int[] restorePersistedRecordings() {
        List<ActiveRecording> persisted = recordingRepository.findByOr(orId);
        int ok = 0;
        int fail = 0;
        for (ActiveRecording saved : persisted) {
            byCase.computeIfAbsent(saved.getCaseId(), k -> new ConcurrentHashMap<>())
                    .put(saved.getSessionId(), saved);
            if (!saved.isActive()) {
                ok++;
                continue;
            }
            if (recorder.isActive(saved.getSessionId())) {
                ok++;
                log.info("Restored active recording {} (worker still running)", saved.getSessionId());
                continue;
            }
            // Worker 无状态：标记失败，避免幽灵录制
            ActiveRecording failed = saved.withFailed("worker lost session after restart");
            store(failed);
            fail++;
            log.warn("Recording {} marked FAILED after restart (worker inactive)", saved.getSessionId());
        }
        log.info(
                "Recording restore finished for {}: ok={}, failed={}, total={}",
                orId,
                ok,
                fail,
                persisted.size());
        return new int[] {ok, fail};
    }

    private void store(ActiveRecording recording) {
        byCase.computeIfAbsent(recording.getCaseId(), k -> new ConcurrentHashMap<>())
                .put(recording.getSessionId(), recording);
        recordingRepository.upsert(orId, recording);
    }

    /** 供 workspace 源卡片标注是否正在录制。 */
    public Map<String, ActiveRecording> activeBySource(String caseId) {
        ActiveRecording active = getActive(caseId);
        if (active == null) {
            return Map.of();
        }
        return Map.of(active.getSourceId(), active);
    }
}
