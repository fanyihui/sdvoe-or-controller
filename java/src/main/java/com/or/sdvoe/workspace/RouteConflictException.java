package com.or.sdvoe.workspace;

/** 目的地已被占用，需要前端确认后抢占。 */
public final class RouteConflictException extends RuntimeException {
    private final ActiveRoute existing;

    public RouteConflictException(ActiveRoute existing) {
        super("destination occupied by " + existing.getSourceName() + "; confirm to steal");
        this.existing = existing;
    }

    public ActiveRoute getExisting() {
        return existing;
    }
}
