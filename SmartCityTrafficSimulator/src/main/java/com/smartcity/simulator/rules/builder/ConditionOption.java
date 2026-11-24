package com.smartcity.simulator.rules.builder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum ConditionOption implements ConditionChoice {

    VEHICLE_SPEED(
            "Vehicle speed (m/s)",
            ValueKind.NON_NEGATIVE_DECIMAL,
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    VEHICLE_TYPE(
            "Vehicle family",
            ValueKind.VEHICLE_FAMILY,
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    VEHICLE_COUNT_ON_ROAD(
            "Vehicles on current road",
            ValueKind.NON_NEGATIVE_INTEGER,
            EnumSet.of(TargetCategory.VEHICLE)
    ),

    ROAD_VEHICLE_COUNT(
            "Vehicles on target road",
            ValueKind.NON_NEGATIVE_INTEGER,
            EnumSet.of(TargetCategory.ROAD)
    ),

    WEATHER(
            "Weather",
            ValueKind.WEATHER,
            EnumSet.allOf(TargetCategory.class)
    ),

    SIMULATION_TIME(
            "Simulation time (seconds)",
            ValueKind.NON_NEGATIVE_DECIMAL,
            EnumSet.allOf(TargetCategory.class)
    ),

    PEDESTRIAN_WAITING_TIME(
            "Pedestrian waiting time (seconds)",
            ValueKind.NON_NEGATIVE_DECIMAL,
            EnumSet.of(TargetCategory.PEDESTRIAN)
    ),

    EMERGENCY_ON_ROAD(
            "Emergency on current/incoming road",
            ValueKind.PRESENCE,
            EnumSet.of(
                    TargetCategory.VEHICLE,
                    TargetCategory.TRAFFIC_LIGHT,
                    TargetCategory.ROAD
            )
    ),

    EMERGENCY_AT_INTERSECTION(
            "Emergency at associated intersection",
            ValueKind.PRESENCE,
            EnumSet.of(
                    TargetCategory.PEDESTRIAN,
                    TargetCategory.TRAFFIC_LIGHT
            )
    );

    private final String label;
    private final ValueKind valueKind;
    private final Set<TargetCategory> targets;

    ConditionOption(
            String label,
            ValueKind valueKind,
            Set<TargetCategory> targets) {

        this.label = label;
        this.valueKind = valueKind;
        this.targets = Set.copyOf(targets);
    }

    public boolean supports(TargetCategory target) {
        return targets.contains(target);
    }

    public ValueKind getValueKind() {
        return valueKind;
    }

    public List<RuleOperator> getOperators() {
        return valueKind.operators();
    }

    @Override
    public String toString() {
        return label;
    }
}