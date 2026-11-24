package com.smartcity.simulator.rules.builder;

import java.util.EnumSet;
import java.util.Set;

public enum ScopeOption {

    ALL(
            "All targets",
            null,
            EnumSet.allOf(TargetCategory.class)
    ),

    VEHICLES_ON_ROAD(
            "Vehicles on road",
            "Road",
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    VEHICLES_APPROACHING_INTERSECTION(
            "Vehicles approaching intersection",
            "Intersection",
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    PEDESTRIANS_AT_INTERSECTION(
            "Pedestrians at intersection",
            "Intersection",
            EnumSet.of(TargetCategory.PEDESTRIAN)
    ),

    LIGHTS_ON_ROAD(
            "Traffic lights on incoming road",
            "Road",
            EnumSet.of(TargetCategory.TRAFFIC_LIGHT)
    ),

    LIGHTS_AT_INTERSECTION(
            "Traffic lights at intersection",
            "Intersection",
            EnumSet.of(TargetCategory.TRAFFIC_LIGHT)
    ),

    ROAD_BY_ID(
            "Selected road",
            "Road",
            EnumSet.of(TargetCategory.ROAD)
    );

    private final String label;
    private final String idLabel;
    private final Set<TargetCategory> targets;

    ScopeOption(
            String label,
            String idLabel,
            Set<TargetCategory> targets) {

        this.label = label;
        this.idLabel = idLabel;
        this.targets = Set.copyOf(targets);
    }

    public boolean supports(TargetCategory category) {
        return targets.contains(category);
    }

    public boolean needsId() {
        return idLabel != null;
    }

    public String getIdLabel() {
        return idLabel;
    }

    @Override
    public String toString() {
        return label;
    }
}