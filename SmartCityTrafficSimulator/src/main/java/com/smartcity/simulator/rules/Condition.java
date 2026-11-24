
package com.smartcity.simulator.rules;

import com.smartcity.simulator.simulation.SimulationContext;

@FunctionalInterface
public interface Condition<T> {

    boolean evaluate(T target, SimulationContext context);
}
