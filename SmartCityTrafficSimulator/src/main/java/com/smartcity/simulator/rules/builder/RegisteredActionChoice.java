package com.smartcity.simulator.rules.builder;

import java.util.Objects;

/** Describes one action plugin in the existing rule editor. */
public record RegisteredActionChoice(
        String id, String label, TargetCategory target, ValueKind valueKind)
        implements ActionChoice {
    public RegisteredActionChoice {
        if (id == null || id.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Action id and label must not be blank");
        }
        Objects.requireNonNull(target);
        Objects.requireNonNull(valueKind);
    }
    @Override public boolean supports(TargetCategory category) { return target == category; }
    @Override public ValueKind getValueKind() { return valueKind; }
    @Override public String toString() { return label; }
}
