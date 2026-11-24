package com.smartcity.simulator.rules.builder;

import java.util.List;
import java.util.Objects;

/** Describes one condition plugin in the existing rule editor. */
public record RegisteredConditionChoice(
        String id, String label, TargetCategory target, ValueKind valueKind)
        implements ConditionChoice {
    public RegisteredConditionChoice {
        if (id == null || id.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Condition id and label must not be blank");
        }
        Objects.requireNonNull(target);
        Objects.requireNonNull(valueKind);
    }
    @Override public boolean supports(TargetCategory category) { return target == category; }
    @Override public ValueKind getValueKind() { return valueKind; }
    @Override public List<RuleOperator> getOperators() { return valueKind.operators(); }
    @Override public String toString() { return label; }
}
