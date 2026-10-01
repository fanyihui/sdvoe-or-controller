package com.or.sdvoe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "or-desk")
public class OrDeskProperties {

    private String configResource = "or-controller.yaml";
    private String scheduleResource = "schedule.yaml";
    private String policyResource = "routing-policies.yaml";

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
}
