package com.smartcity.simulator.rules.builder;

public enum TargetCategory {
    VEHICLE("Vehicle"),
    PEDESTRIAN("Pedestrian"),
    TRAFFIC_LIGHT("Traffic light"),
    ROAD("Road");

    private final String label;

    TargetCategory(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}