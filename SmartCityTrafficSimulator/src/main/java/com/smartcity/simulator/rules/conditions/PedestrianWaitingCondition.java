
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.simulation.SimulationContext;
import java.util.Objects;

public final class PedestrianWaitingCondition
        implements Condition<Pedestrian> {

    private final Comparison comparison;
    private final double thresholdSeconds;

    public PedestrianWaitingCondition(
            Comparison comparison,
            double thresholdSeconds) {

        this.comparison = Objects.requireNonNull(comparison);

        if (!Double.isFinite(thresholdSeconds)
                || thresholdSeconds < 0) {
            throw new InvalidRuleException(
                    "Waiting threshold must be finite and non-negative");
        }

        this.thresholdSeconds = thresholdSeconds;
    }

    @Override
    public boolean evaluate(
            Pedestrian target,
            SimulationContext context) {

        double waiting = context.getRuleObservations()
                .pedestrian(target.getId()).waitingSeconds();

        return comparison.test(waiting, thresholdSeconds);
    }
}
