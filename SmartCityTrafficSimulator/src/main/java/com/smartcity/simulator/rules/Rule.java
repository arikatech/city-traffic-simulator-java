
package com.smartcity.simulator.rules;

import com.smartcity.simulator.simulation.SimulationContext;
import java.util.Objects;

public final class Rule<T> {

    private final String id;
    private final String name;
    private final int priority;
    private final boolean enabled;

    private final TargetScope<T> scope;
    private final Condition<T> condition;
    private final Action<T> action;

    public Rule(
            String id,
            String name,
            int priority,
            boolean enabled,
            TargetScope<T> scope,
            Condition<T> condition,
            Action<T> action) {

        if (id == null || id.isBlank()
                || name == null || name.isBlank()) {

            throw new InvalidRuleException(
                    "Rule ID and name cannot be blank");
        }

        if (scope == null || condition == null || action == null) {
            throw new InvalidRuleException(
                    "Rule scope, condition and action are required");
        }

        this.id = id.strip();
        this.name = name.strip();
        this.priority = priority;
        this.enabled = enabled;
        this.scope = scope;
        this.condition = condition;
        this.action = action;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public TargetScope<T> getScope() {
        return scope;
    }

    // Return a new Rule with changed enabled status.
    public Rule<T> withEnabled(boolean value) {

        return new Rule<>(
                id,
                name,
                priority,
                value,
                scope,
                condition,
                action
        );
    }

    public boolean evaluate(
            T target,
            SimulationContext ctx,
            RuleEffects effects) {

        Objects.requireNonNull(target);
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(effects);

        if (!enabled
                || !scope.includes(target, ctx)
                || !condition.evaluate(target, ctx)) {

            return false;
        }

        effects.collectForRule(
            id,
            priority,
            () -> action.collect(target, ctx, effects)
        );

        effects.recordMatch(id, priority);

        return true;
    }
}
