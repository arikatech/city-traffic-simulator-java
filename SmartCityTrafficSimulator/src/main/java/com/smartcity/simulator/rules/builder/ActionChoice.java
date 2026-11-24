package com.smartcity.simulator.rules.builder;

/** Common UI/compiler descriptor for built-in and externally registered actions. */
public interface ActionChoice {
    boolean supports(TargetCategory target);
    ValueKind getValueKind();
}
