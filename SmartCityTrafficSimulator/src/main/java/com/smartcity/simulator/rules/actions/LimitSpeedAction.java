
package com.smartcity.simulator.rules.actions;

import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;

public final class LimitSpeedAction implements Action<Vehicle> {

    private final double fraction;

    public LimitSpeedAction(double fraction) {

        if (!Double.isFinite(fraction)
                || fraction <= 0
                || fraction > 1) {

            throw new InvalidRuleException(
                    "Speed fraction must be in (0, 1]");
        }

        this.fraction = fraction;
    }

    @Override
    public void collect(
            Vehicle target,
            SimulationContext ctx,
            RuleEffects effects) {

        ctx.getRuleObservations().vehicle(target.getId());

        effects.limitVehicleSpeed(target.getId(), fraction);
    }
}
