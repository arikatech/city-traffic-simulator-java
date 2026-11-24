
package com.smartcity.simulator.rules;

import com.smartcity.simulator.simulation.SimulationContext;
import java.util.Objects;

public final class NotCondition<T> implements Condition<T> {

    private final Condition<T> operand;

    public NotCondition(Condition<T> operand) {
        this.operand = Objects.requireNonNull(operand, "operand condition");
    }

    @Override
    public boolean evaluate(T target, SimulationContext context) {
        return !operand.evaluate(target, context);
    }
}
