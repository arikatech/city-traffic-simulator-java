
package com.smartcity.simulator.rules.conditions;

public enum Comparison {
    LESS_THAN,
    LESS_OR_EQUAL,
    EQUAL,
    GREATER_OR_EQUAL,
    GREATER_THAN;

    private static final double EPSILON = 1e-6;

    public boolean test(double actual, double expected) {
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) {
            throw new IllegalArgumentException(
                    "Comparison values must be finite.");
        }

        return switch (this) {
            case LESS_THAN -> actual < expected;
            case LESS_OR_EQUAL -> actual <= expected;
            case EQUAL -> Math.abs(actual - expected) <= EPSILON;
            case GREATER_OR_EQUAL -> actual >= expected;
            case GREATER_THAN -> actual > expected;
        };
    }
}
