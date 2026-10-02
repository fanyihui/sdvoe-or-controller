package com.or.sdvoe.workspace;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 工作空间内一路软件录制会话。 */
public final class ActiveRecording {

    public enum Status {
        PREPARING,
        RECORDING,
        STOPPING,
        STOPPED,
        FAILED
    }

    public enum Mode {
        SOFTWARE
    }

    private final String sessionId;
    private final String caseId;
    private final String sourceId;
    private final String sourceName;
    private final String deviceId;
    private final String streamId;
    private final Mode mode;
    private final String recorderTargetId;
    private final Status status;
    private final String artifactUri;
    private final Long artifactBytes;
    private final String operator;
    private final String errorMessage;
    private final Instant startedAt;
    private final Instant stoppedAt;
    private final Instant updatedAt;

    public ActiveRecording(
            String sessionId,
            String caseId,
            String sourceId,
            String sourceName,
            String deviceId,
            String streamId,
            Mode mode,
            String recorderTargetId,
            Status status,
            String artifactUri,
            Long artifactBytes,
            String operator,
            String errorMessage,
            Instant startedAt,
            Instant stoppedAt,
            Instant updatedAt) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.caseId = Objects.requireNonNull(caseId);
        this.sourceId = Objects.requireNonNull(sourceId);
        this.sourceName = sourceName;
        this.deviceId = deviceId;
        this.streamId = streamId;
        this.mode = mode == null ? Mode.SOFTWARE : mode;
        this.recorderTargetId = recorderTargetId;
        this.status = Objects.requireNonNull(status);
        this.artifactUri = artifactUri;
        this.artifactBytes = artifactBytes;
        this.operator = operator;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.stoppedAt = stoppedAt;
        this.updatedAt = updatedAt != null ? updatedAt : this.startedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getStreamId() {
        return streamId;
    }

    public Mode getMode() {
        return mode;
    }

    public String getRecorderTargetId() {
        return recorderTargetId;
    }

    public Status getStatus() {
        return status;
    }

    public String getArtifactUri() {
        return artifactUri;
    }

    public Long getArtifactBytes() {
        return artifactBytes;
    }

    public String getOperator() {
        return operator;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == Status.PREPARING || status == Status.RECORDING || status == Status.STOPPING;
    }

    public long elapsedSec() {
        Instant end = stoppedAt != null ? stoppedAt : Instant.now();
        return Math.max(0, Duration.between(startedAt, end).getSeconds());
    }

    public ActiveRecording withStatus(Status newStatus) {
        return new ActiveRecording(
                sessionId,
                caseId,
                sourceId,
                sourceName,
                deviceId,
                streamId,
                mode,
                recorderTargetId,
                newStatus,
                artifactUri,
                artifactBytes,
                operator,
                errorMessage,
                startedAt,
                stoppedAt,
                Instant.now());
    }

    public ActiveRecording withRecording() {
        return withStatus(Status.RECORDING);
    }

    public ActiveRecording withStopped(String uri, Long bytes) {
        Instant now = Instant.now();
        return new ActiveRecording(
                sessionId,
                caseId,
                sourceId,
                sourceName,
                deviceId,
                streamId,
                mode,
                recorderTargetId,
                Status.STOPPED,
                uri,
                bytes,
                operator,
                null,
                startedAt,
                now,
                now);
    }

    public ActiveRecording withFailed(String message) {
        Instant now = Instant.now();
        return new ActiveRecording(
                sessionId,
                caseId,
                sourceId,
                sourceName,
                deviceId,
                streamId,
                mode,
                recorderTargetId,
                Status.FAILED,
                artifactUri,
                artifactBytes,
                operator,
                message,
                startedAt,
                now,
                now);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", sessionId);
        m.put("caseId", caseId);
        m.put("sourceId", sourceId);
        m.put("sourceName", sourceName);
        m.put("deviceId", deviceId);
        m.put("streamId", streamId);
        m.put("mode", mode.name());
        m.put("recorderTargetId", recorderTargetId);
        m.put("status", status.name());
        m.put("active", isActive());
        m.put("artifactUri", artifactUri);
        m.put("artifactBytes", artifactBytes);
        m.put("operator", operator);
        m.put("errorMessage", errorMessage);
        m.put("startedAt", startedAt.toString());
        m.put("stoppedAt", stoppedAt == null ? null : stoppedAt.toString());
        m.put("updatedAt", updatedAt.toString());
        m.put("elapsedSec", elapsedSec());
        return m;
    }
}
