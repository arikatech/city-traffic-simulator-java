package com.smartcity.simulator.rules.management;

import com.smartcity.simulator.rules.builder.TargetCategory;

/** Immutable data for UI; no mutable domain objects leak into the table. */
public record RuleSummary(
        String id, String name, TargetCategory target,
        String scope, String expression, String action,
        int priority, boolean enabled, boolean editable) {
}
