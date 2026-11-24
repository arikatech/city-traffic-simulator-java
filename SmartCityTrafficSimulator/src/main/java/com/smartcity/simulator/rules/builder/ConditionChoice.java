package com.smartcity.simulator.rules.builder;

import java.util.List;

/** Common UI/compiler descriptor for built-in and externally registered conditions. */
public interface ConditionChoice {
    boolean supports(TargetCategory target);
    ValueKind getValueKind();
    List<RuleOperator> getOperators();
}
