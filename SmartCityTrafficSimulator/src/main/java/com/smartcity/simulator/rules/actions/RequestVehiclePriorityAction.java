
package com.smartcity.simulator.rules.actions;

import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;

public final class RequestVehiclePriorityAction
        implements Action<Vehicle> {

    @Override
    public void collect(
            Vehicle target,
            SimulationContext ctx,
            RuleEffects effects) {

        String roadId = ctx.getRuleObservations()
                .vehicle(target.getId()).roadId();

        String intersectionId = ctx.getRuleObservations()
                .intersectionForIncomingRoad(roadId);

        if (intersectionId != null) {
            effects.requestVehiclePriority(
                    target.getId(), intersectionId);
        }
    }
}
