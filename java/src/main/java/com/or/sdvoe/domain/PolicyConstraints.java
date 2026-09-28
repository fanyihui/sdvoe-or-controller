package com.or.sdvoe.domain;

public class PolicyConstraints {
    private int maxLatencyMs = 100;
    private int maxBridgeHops = 1;
    private boolean requireLossless = true;
    private SourceType requireGenlockWith;
    private boolean allowMulticastFanout;
    private int preferMulticastWhenSinksGte = 2;
    private LockMode lockMode = LockMode.NONE;
    private boolean capabilityDriven;

    public int getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(int maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public int getMaxBridgeHops() {
        return maxBridgeHops;
    }

    public void setMaxBridgeHops(int maxBridgeHops) {
        this.maxBridgeHops = maxBridgeHops;
    }

    public boolean isRequireLossless() {
        return requireLossless;
    }

    public void setRequireLossless(boolean requireLossless) {
        this.requireLossless = requireLossless;
    }

    public SourceType getRequireGenlockWith() {
        return requireGenlockWith;
    }

    public void setRequireGenlockWith(SourceType requireGenlockWith) {
        this.requireGenlockWith = requireGenlockWith;
    }

    public boolean isAllowMulticastFanout() {
        return allowMulticastFanout;
    }

    public void setAllowMulticastFanout(boolean allowMulticastFanout) {
        this.allowMulticastFanout = allowMulticastFanout;
    }

    public int getPreferMulticastWhenSinksGte() {
        return preferMulticastWhenSinksGte;
    }

    public void setPreferMulticastWhenSinksGte(int preferMulticastWhenSinksGte) {
        this.preferMulticastWhenSinksGte = preferMulticastWhenSinksGte;
    }

    public LockMode getLockMode() {
        return lockMode;
    }

    public void setLockMode(LockMode lockMode) {
        this.lockMode = lockMode;
    }

    public boolean isCapabilityDriven() {
        return capabilityDriven;
    }

    public void setCapabilityDriven(boolean capabilityDriven) {
        this.capabilityDriven = capabilityDriven;
    }

    public PolicyConstraints copy() {
        PolicyConstraints c = new PolicyConstraints();
        c.maxLatencyMs = maxLatencyMs;
        c.maxBridgeHops = maxBridgeHops;
        c.requireLossless = requireLossless;
        c.requireGenlockWith = requireGenlockWith;
        c.allowMulticastFanout = allowMulticastFanout;
        c.preferMulticastWhenSinksGte = preferMulticastWhenSinksGte;
        c.lockMode = lockMode;
        c.capabilityDriven = capabilityDriven;
        return c;
    }
}
