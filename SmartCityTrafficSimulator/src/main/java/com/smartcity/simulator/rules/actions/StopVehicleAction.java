
package com.smartcity.simulator.rules.actions;

import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;

public final class StopVehicleAction implements Action<Vehicle> {

    @Override
    public void collect(
            Vehicle target,
            SimulationContext ctx,
            RuleEffects effects) {

        ctx.getRuleObservations().vehicle(target.getId());

        effects.stopVehicle(target.getId());
    }
}
