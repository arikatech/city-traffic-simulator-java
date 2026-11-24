
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.simulation.SimulationContext;

public final class WeatherCondition<T> implements Condition<T> {

    private final Weather expected;
    private final EqualityComparison comparison;

    public WeatherCondition(
            EqualityComparison comparison,
            Weather expected) {

        if (comparison == null || expected == null) {
            throw new InvalidRuleException(
                    "Weather comparison and value are required.");
        }

        this.comparison = comparison;
        this.expected = expected;
    }

    @Override
    public boolean evaluate(T target, SimulationContext context) {
        return comparison.test(context.getWeather(), expected);
    }
}
