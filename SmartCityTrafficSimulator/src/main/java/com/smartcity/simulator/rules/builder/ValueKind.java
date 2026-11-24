package com.smartcity.simulator.rules.builder;

import java.util.List;

public enum ValueKind {

    NONE,
    NON_NEGATIVE_DECIMAL,
    NON_NEGATIVE_INTEGER,
    WEATHER,
    VEHICLE_FAMILY,
    PRESENCE,
    SPEED_FRACTION,
    SIGNAL_PHASE;

    public String inputHint() {

        return switch (this) {

            case NONE ->
                "No value required";

            case NON_NEGATIVE_DECIMAL ->
                "Finite number >= 0";

            case NON_NEGATIVE_INTEGER ->
                "Whole number >= 0";

            case WEATHER ->
                "Choose DRY or RAIN";

            case VEHICLE_FAMILY ->
                "Choose CAR, BUS, TRUCK or EMERGENCY";

            case PRESENCE ->
                "True when present; use NOT for absence";

            case SPEED_FRACTION ->
                "Finite fraction > 0 and <= 1 (e.g. 0.8)";

            case SIGNAL_PHASE ->
                "Choose GREEN or RED";
        };
    }

    public List<RuleOperator> operators() {

        return switch (this) {

            case NON_NEGATIVE_DECIMAL,
                 NON_NEGATIVE_INTEGER -> List.of(
                    RuleOperator.LESS_THAN,
                    RuleOperator.LESS_OR_EQUAL,
                    RuleOperator.EQUAL,
                    RuleOperator.GREATER_OR_EQUAL,
                    RuleOperator.GREATER_THAN
            );

            case WEATHER, VEHICLE_FAMILY -> List.of(
                    RuleOperator.EQUAL,
                    RuleOperator.NOT_EQUAL
            );

            case PRESENCE,
                 SPEED_FRACTION,
                 SIGNAL_PHASE,
                 NONE -> List.of();
        };
    }
}