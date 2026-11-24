
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.Objects;

public final class VehicleCountCondition implements Condition<Vehicle> {

    private final Comparison comparison;
    private final int threshold;

    public VehicleCountCondition(
            Comparison comparison, int threshold) {

        this.comparison = Objects.requireNonNull(comparison);

        if (threshold < 0) {
            throw new InvalidRuleException(
                    "Vehicle count must be non-negative");
        }

        this.threshold = threshold;
    }

    @Override
    public boolean evaluate(Vehicle target, SimulationContext context) {

        String roadId = context.getRuleObservations()
                .vehicle(target.getId()).roadId();

        return roadId != null
                && comparison.test(
                        context.getVehicleCountOnRoad(roadId),
                        threshold);
    }
}
