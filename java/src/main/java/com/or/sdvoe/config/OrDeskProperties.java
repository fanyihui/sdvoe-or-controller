package com.or.sdvoe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "or-desk")
public class OrDeskProperties {

    private String configResource = "or-controller.yaml";
    private String scheduleResource = "schedule.yaml";
    private String policyResource = "routing-policies.yaml";
    /** 软件录制输出目录（P0 stub 写占位文件）。 */
    private String recordingOutputDir = "data/recordings";
    private String recordingWorkerId = "software-recorder-stub";

    public String getConfigResource() {
        return configResource;
    }

    public void setConfigResource(String configResource) {
        this.configResource = configResource;
    }

    public String getScheduleResource() {
        return scheduleResource;
    }

    public void setScheduleResource(String scheduleResource) {
        this.scheduleResource = scheduleResource;
    }

    public String getPolicyResource() {
        return policyResource;
    }

    public void setPolicyResource(String policyResource) {
        this.policyResource = policyResource;
    }

    public String getRecordingOutputDir() {
        return recordingOutputDir;
    }

    public void setRecordingOutputDir(String recordingOutputDir) {
        this.recordingOutputDir = recordingOutputDir;
    }

    public String getRecordingWorkerId() {
        return recordingWorkerId;
    }

    public void setRecordingWorkerId(String recordingWorkerId) {
        this.recordingWorkerId = recordingWorkerId;
    }
}
