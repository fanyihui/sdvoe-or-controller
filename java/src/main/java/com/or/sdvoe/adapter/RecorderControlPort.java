package com.or.sdvoe.adapter;

/** 录制控制端口（P0：软件 Worker；后续可换硬件实现）。 */
public interface RecorderControlPort {

    /** @return worker/target id */
    String targetId();

    StartResult start(StartRequest request);

    StopResult stop(String sessionId);

    /** 探活进行中的会话；不存在或已结束返回 false。 */
    boolean isActive(String sessionId);

    record StartRequest(
            String sessionId,
            String orId,
            String caseId,
            String sourceId,
            String streamId,
            String outputUriHint) {
    }

    record StartResult(boolean ok, String artifactUri, String message) {
        public static StartResult success(String artifactUri) {
            return new StartResult(true, artifactUri, "started");
        }

        public static StartResult failure(String message) {
            return new StartResult(false, null, message);
        }
    }

    record StopResult(boolean ok, String artifactUri, Long artifactBytes, String message) {
        public static StopResult success(String artifactUri, Long bytes) {
            return new StopResult(true, artifactUri, bytes, "stopped");
        }

        public static StopResult failure(String message) {
            return new StopResult(false, null, null, message);
        }
    }
}
