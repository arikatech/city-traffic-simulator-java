
package com.smartcity.simulator.rules;

import com.smartcity.simulator.simulation.SimulationContext;

@FunctionalInterface
public interface Action<T> {

    void collect(
            T target,
            SimulationContext context,
            RuleEffects effects);
}
