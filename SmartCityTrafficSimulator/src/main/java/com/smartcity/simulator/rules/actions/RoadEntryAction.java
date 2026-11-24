
package com.smartcity.simulator.rules.actions;

import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.rules.RuleEffects.RoadEntryDecision;
import com.smartcity.simulator.simulation.SimulationContext;

public final class RoadEntryAction implements Action<Road> {

    private final RoadEntryDecision decision;

    public RoadEntryAction(RoadEntryDecision decision) {

        if (decision == null) {
            throw new InvalidRuleException(
                    "Road-entry decision is required");
        }

        this.decision = decision;
    }

    public static RoadEntryAction close() {
        return new RoadEntryAction(RoadEntryDecision.CLOSE);
    }

    public static RoadEntryAction allow() {
        return new RoadEntryAction(RoadEntryDecision.ALLOW);
    }

    @Override
    public void collect(
            Road target,
            SimulationContext ctx,
            RuleEffects effects) {

        ctx.getRoadFacts(target.getId());

        effects.requestRoadEntry(target.getId(), decision);
    }
}
