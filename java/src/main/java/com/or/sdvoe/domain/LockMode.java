package com.or.sdvoe.domain;

public enum LockMode {
    NONE("none"),
    SOFT("soft"),
    HARD("hard");

    private final String value;

    LockMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LockMode fromValue(String raw) {
        if (raw == null) {
            return NONE;
        }
        for (LockMode mode : values()) {
            if (mode.value.equalsIgnoreCase(raw) || mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown lock mode: " + raw);
    }
}
