package com.smartcity.simulator.rules.builder;

import java.util.EnumSet;
import java.util.Set;

public enum ActionOption implements ActionChoice {

    LIMIT_SPEED(
            "Limit speed (fraction of baseline)",
            ValueKind.SPEED_FRACTION,
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    STOP_VEHICLE(
            "Stop vehicle for this tick",
            ValueKind.NONE,
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    REQUEST_VEHICLE_PRIORITY(
            "Request vehicle priority",
            ValueKind.NONE,
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    REQUEST_PEDESTRIAN_PRIORITY(
            "Request pedestrian crossing priority",
            ValueKind.NONE,
            EnumSet.of(TargetCategory.PEDESTRIAN)
    ),

    REQUEST_SIGNAL_PHASE(
            "Request safe signal phase",
            ValueKind.SIGNAL_PHASE,
            EnumSet.of(TargetCategory.TRAFFIC_LIGHT)
    ),

    CLOSE_ROAD_ENTRY(
            "Close road entry",
            ValueKind.NONE,
            EnumSet.of(TargetCategory.ROAD)
    ),

    ALLOW_ROAD_ENTRY(
            "Allow rule-restricted road entry",
            ValueKind.NONE,
            EnumSet.of(TargetCategory.ROAD)
    );

    private final String label;
    private final ValueKind valueKind;
    private final Set<TargetCategory> targets;

    ActionOption(
            String label,
            ValueKind valueKind,
            Set<TargetCategory> targets) {

        this.label = label;
        this.valueKind = valueKind;
        this.targets = Set.copyOf(targets);
    }

    public boolean supports(TargetCategory category) {
        return targets.contains(category);
    }

    public ValueKind getValueKind() {
        return valueKind;
    }

    @Override
    public String toString() {
        return label;
    }
}