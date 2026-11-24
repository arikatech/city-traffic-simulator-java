
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.simulation.SimulationContext;
import java.util.Objects;

public final class SimulationTimeCondition<T>
        implements Condition<T> {

    private final Comparison comparison;
    private final double thresholdSeconds;

    public SimulationTimeCondition(
            Comparison comparison,
            double thresholdSeconds) {

        this.comparison = Objects.requireNonNull(comparison);

        if (!Double.isFinite(thresholdSeconds)
                || thresholdSeconds < 0) {
            throw new InvalidRuleException(
                    "Simulation time threshold must be finite and non-negative.");
        }

        this.thresholdSeconds = thresholdSeconds;
    }

    @Override
    public boolean evaluate(T target, SimulationContext context) {
        return comparison.test(
                context.getElapsedSeconds(),
                thresholdSeconds);
    }
}
