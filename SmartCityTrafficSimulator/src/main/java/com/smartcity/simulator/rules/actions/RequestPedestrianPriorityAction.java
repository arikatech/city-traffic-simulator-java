
package com.smartcity.simulator.rules.actions;

import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.simulation.SimulationContext;

public final class RequestPedestrianPriorityAction
        implements Action<Pedestrian> {

    @Override
    public void collect(
            Pedestrian target,
            SimulationContext ctx,
            RuleEffects effects) {

        String intersectionId = ctx.getRuleObservations()
                .pedestrian(target.getId()).intersectionId();

        if (intersectionId != null) {
            effects.requestPedestrianPriority(
                    target.getId(), intersectionId);
        }
    }
}
