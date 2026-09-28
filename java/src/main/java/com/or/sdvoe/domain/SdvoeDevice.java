package com.or.sdvoe.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 手术室内一台 SDVoE 设备（Encoder/Decoder/Transceiver/Switch）。
 */
public final class SdvoeDevice {

    private final String id;
    private final String name;
    private final String orId;
    private final SdvoeDeviceRole role;
    private final String ipAddress;
    private final String macAddress;
    private final String model;
    private final String firmware;
    private final String location;
    private final DeviceOnlineStatus status;
    private final boolean signalPresent;
    private final String streamId;
    private final Instant lastSeenAt;
    private final Map<String, String> tags;

    private SdvoeDevice(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.name = b.name != null ? b.name : b.id;
        this.orId = Objects.requireNonNull(b.orId, "orId");
        this.role = b.role != null ? b.role : SdvoeDeviceRole.UNKNOWN;
        this.ipAddress = b.ipAddress;
        this.macAddress = b.macAddress;
        this.model = b.model;
        this.firmware = b.firmware;
        this.location = b.location;
        this.status = b.status != null ? b.status : DeviceOnlineStatus.UNKNOWN;
        this.signalPresent = b.signalPresent;
        this.streamId = b.streamId;
        this.lastSeenAt = b.lastSeenAt != null ? b.lastSeenAt : Instant.now();
        this.tags = Map.copyOf(b.tags);
    }

    public static Builder builder(String id, String orId) {
        return new Builder(id, orId);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrId() {
        return orId;
    }

    public SdvoeDeviceRole getRole() {
        return role;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getModel() {
        return model;
    }

    public String getFirmware() {
        return firmware;
    }

    public String getLocation() {
        return location;
    }

    public DeviceOnlineStatus getStatus() {
        return status;
    }

    public boolean isSignalPresent() {
        return signalPresent;
    }

    public String getStreamId() {
        return streamId;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("orId", orId);
        m.put("role", role.name());
        m.put("ipAddress", ipAddress);
        m.put("macAddress", macAddress);
        m.put("model", model);
        m.put("firmware", firmware);
        m.put("location", location);
        m.put("status", status.name());
        m.put("signalPresent", signalPresent);
        m.put("streamId", streamId);
        m.put("lastSeenAt", lastSeenAt.toString());
        m.put("tags", tags);
        return m;
    }

    public static final class Builder {
        private final String id;
        private final String orId;
        private String name;
        private SdvoeDeviceRole role;
        private String ipAddress;
        private String macAddress;
        private String model;
        private String firmware;
        private String location;
        private DeviceOnlineStatus status;
        private boolean signalPresent;
        private String streamId;
        private Instant lastSeenAt;
        private final Map<String, String> tags = new LinkedHashMap<>();

        private Builder(String id, String orId) {
            this.id = id;
            this.orId = orId;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder role(SdvoeDeviceRole role) {
            this.role = role;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder macAddress(String macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder firmware(String firmware) {
            this.firmware = firmware;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder status(DeviceOnlineStatus status) {
            this.status = status;
            return this;
        }

        public Builder signalPresent(boolean signalPresent) {
            this.signalPresent = signalPresent;
            return this;
        }

        public Builder streamId(String streamId) {
            this.streamId = streamId;
            return this;
        }

        public Builder lastSeenAt(Instant lastSeenAt) {
            this.lastSeenAt = lastSeenAt;
            return this;
        }

        public Builder tag(String key, String value) {
            if (key != null && value != null) {
                this.tags.put(key, value);
            }
            return this;
        }

        public Builder tags(Map<String, String> tags) {
            if (tags != null) {
                this.tags.putAll(tags);
            }
            return this;
        }

        public SdvoeDevice build() {
            return new SdvoeDevice(this);
        }
    }
}
