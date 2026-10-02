package com.or.sdvoe.workspace;

/** 同手术已有进行中的录制会话。 */
public final class RecordingConflictException extends RuntimeException {

    private final ActiveRecording existing;

    public RecordingConflictException(ActiveRecording existing) {
        super("recording already active: " + existing.getSessionId() + " source=" + existing.getSourceId());
        this.existing = existing;
    }

    public ActiveRecording getExisting() {
        return existing;
    }
}
