
package com.smartcity.simulator.rules.conditions;

public enum EqualityComparison {
    EQUAL,
    NOT_EQUAL;

    public <E extends Enum<E>> boolean test(E actual, E expected) {
        return switch (this) {
            case EQUAL -> actual == expected;
            case NOT_EQUAL -> actual != expected;
        };
    }
}
