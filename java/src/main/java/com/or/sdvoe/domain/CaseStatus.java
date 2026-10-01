package com.or.sdvoe.domain;

public enum CaseStatus {
    SCHEDULED,
    PREP,
    IN_PROGRESS,
    CLOSING,
    DONE,
    CANCELLED;

    public String labelZh() {
        return switch (this) {
            case SCHEDULED -> "待接台";
            case PREP -> "术前准备";
            case IN_PROGRESS -> "进行中";
            case CLOSING -> "关腹/收尾";
            case DONE -> "已完成";
            case CANCELLED -> "已取消";
        };
    }
}
