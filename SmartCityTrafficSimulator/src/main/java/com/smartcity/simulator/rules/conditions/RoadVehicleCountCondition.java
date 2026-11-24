
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.simulation.SimulationContext;
import java.util.Objects;

public final class RoadVehicleCountCondition implements Condition<Road> {

    private final Comparison comparison;
    private final int threshold;

    public RoadVehicleCountCondition(
            Comparison comparison, int threshold) {

        this.comparison = Objects.requireNonNull(comparison);

        if (threshold < 0) {
            throw new InvalidRuleException(
                    "Vehicle count must be non-negative");
        }

        this.threshold = threshold;
    }

    @Override
    public boolean evaluate(Road target, SimulationContext context) {
        return comparison.test(
                context.getVehicleCountOnRoad(target.getId()),
                threshold);
    }
}
