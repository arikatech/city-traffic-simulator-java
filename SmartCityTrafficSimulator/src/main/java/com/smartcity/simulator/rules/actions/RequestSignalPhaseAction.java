
package com.smartcity.simulator.rules.actions;

import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.signals.SignalState;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.simulation.SimulationContext;

public final class RequestSignalPhaseAction
        implements Action<TrafficLight> {

    private final SignalState desiredPhase;

    public RequestSignalPhaseAction(SignalState desiredPhase) {

        if (desiredPhase != SignalState.GREEN
                && desiredPhase != SignalState.RED) {

            throw new InvalidRuleException(
                    "Request GREEN or RED; "
                    + "YELLOW is controller-managed");
        }

        this.desiredPhase = desiredPhase;
    }

    @Override
    public void collect(
            TrafficLight target,
            SimulationContext ctx,
            RuleEffects effects) {

        String roadId = target.getIncomingRoad().getId();

        String intersectionId = ctx.getRuleObservations()
                .intersectionForIncomingRoad(roadId);

        if (intersectionId != null) {
            effects.requestSignalPhase(
                    roadId, intersectionId, desiredPhase);
        }
    }
}
