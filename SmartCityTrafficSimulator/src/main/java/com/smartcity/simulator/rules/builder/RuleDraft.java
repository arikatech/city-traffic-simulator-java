package com.smartcity.simulator.rules.builder;

public record RuleDraft(
        String name,
        TargetCategory target,
        ScopeOption scope,
        String scopeId,
        ConditionDraft firstCondition,
        ConditionDraft secondCondition,
        Connector connector,
        ActionDraft action,
        String priority,
        boolean enabled) {

    public enum Connector {
        AND, OR
    }

    public record ConditionDraft(
            ConditionChoice condition,
            RuleOperator operator,
            String rawValue,
            boolean negated) {
    }

    public record ActionDraft(
            ActionChoice action,
            String rawValue) {
    }
}