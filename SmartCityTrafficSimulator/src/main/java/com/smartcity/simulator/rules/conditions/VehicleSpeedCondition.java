
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.Objects;

public final class VehicleSpeedCondition implements Condition<Vehicle> {

    private final Comparison comparison;
    private final double threshold;

    public VehicleSpeedCondition(
            Comparison comparison, double threshold) {

        this.comparison = Objects.requireNonNull(comparison);

        if (!Double.isFinite(threshold) || threshold < 0) {
            throw new InvalidRuleException(
                    "Speed threshold must be finite and non-negative");
        }

        this.threshold = threshold;
    }

    @Override
    public boolean evaluate(Vehicle target, SimulationContext context) {
        double speed = context.getRuleObservations()
                .vehicle(target.getId()).speedMetersPerSecond();

        return comparison.test(speed, threshold);
    }
}
