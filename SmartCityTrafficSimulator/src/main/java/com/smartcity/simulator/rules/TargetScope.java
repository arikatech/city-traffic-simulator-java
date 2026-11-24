
package com.smartcity.simulator.rules;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.simulation.SimulationContext;

@FunctionalInterface
public interface TargetScope<T> {

    boolean includes(T target, SimulationContext context);

    default void validate(CityMap city) {
        // An unrestricted scope needs no location validation.
    }
}
