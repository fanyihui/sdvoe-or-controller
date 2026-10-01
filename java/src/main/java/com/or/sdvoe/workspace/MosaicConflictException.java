package com.or.sdvoe.workspace;

/** 目的地已被单路路由或其它拼屏占用。 */
public final class MosaicConflictException extends RuntimeException {

    private final String destinationId;
    private final String existingKind;
    private final Object existing;

    public MosaicConflictException(String destinationId, String existingKind, Object existing) {
        super("destination occupied by " + existingKind + ": " + destinationId);
        this.destinationId = destinationId;
        this.existingKind = existingKind;
        this.existing = existing;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public String getExistingKind() {
        return existingKind;
    }

    public Object getExisting() {
        return existing;
    }
}
