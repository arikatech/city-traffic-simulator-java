package com.smartcity.simulator.rules.management;

import com.smartcity.simulator.rules.builder.RuleDraft;

/** Human-readable descriptions from the same draft that built the actual rule. */
public final class RuleDraftDescriptions {

    private RuleDraftDescriptions() {}

    public static String scope(RuleDraft draft) {
        return draft.scope().toString()
                + (draft.scope().needsId() ? " [" + draft.scopeId() + "]" : "");
    }

    private static String condition(RuleDraft.ConditionDraft condition) {
        String description = condition.condition().toString();
        if (condition.operator() != null) {
            description += " " + condition.operator() + " " + condition.rawValue();
        }
        return condition.negated() ? "NOT (" + description + ")" : description;
    }

    public static String expression(RuleDraft draft) {
        String first = condition(draft.firstCondition());
        return draft.secondCondition() == null ? first
                : first + " " + draft.connector() + " " + condition(draft.secondCondition());
    }

    public static String action(RuleDraft draft) {
        String result = draft.action().action().toString();
        String parameter = draft.action().rawValue();
        return parameter == null || parameter.isBlank() ? result : result + " = " + parameter;
    }
}
