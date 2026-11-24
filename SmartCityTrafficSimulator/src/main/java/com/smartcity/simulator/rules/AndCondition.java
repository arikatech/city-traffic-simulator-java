
package com.smartcity.simulator.rules;

import com.smartcity.simulator.simulation.SimulationContext;
import java.util.Objects;

public final class AndCondition<T> implements Condition<T> {

    private final Condition<T> left;
    private final Condition<T> right;

    public AndCondition(Condition<T> left, Condition<T> right) {
        this.left = Objects.requireNonNull(left, "left condition");
        this.right = Objects.requireNonNull(right, "right condition");
    }

    @Override
    public boolean evaluate(T target, SimulationContext context) {
        return left.evaluate(target, context)
                && right.evaluate(target, context);
    }
}
