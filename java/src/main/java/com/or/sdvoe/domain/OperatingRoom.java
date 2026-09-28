package com.or.sdvoe.domain;

import java.util.Objects;

/** 本控制器所属手术室上下文。 */
public final class OperatingRoom {

    private final String id;
    private final String name;
    private final String building;
    private final String floor;

    public OperatingRoom(String id, String name) {
        this(id, name, null, null);
    }

    public OperatingRoom(String id, String name, String building, String floor) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name != null ? name : id;
        this.building = building;
        this.floor = floor;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBuilding() {
        return building;
    }

    public String getFloor() {
        return floor;
    }
}
