
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;

public final class VehicleTypeCondition implements Condition<Vehicle> {

    private final EqualityComparison comparison;
    private final VehicleFamily expected;

    public VehicleTypeCondition(
            EqualityComparison comparison,
            VehicleFamily expected) {

        if (comparison == null || expected == null
                || expected == VehicleFamily.OTHER) {
            throw new InvalidRuleException(
                    "Choose a valid vehicle family and comparison");
        }

        this.comparison = comparison;
        this.expected = expected;
    }

    @Override
    public boolean evaluate(Vehicle target, SimulationContext context) {
        VehicleFamily actual = context.getRuleObservations()
                .vehicle(target.getId()).family();

        return comparison.test(actual, expected);
    }
}
