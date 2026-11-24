package com.smartcity.simulator.rules.builder;

public enum RuleOperator {
    LESS_THAN("<"),
    LESS_OR_EQUAL("<="),
    EQUAL("="),
    NOT_EQUAL("!="),
    GREATER_OR_EQUAL(">="),
    GREATER_THAN(">");

    private final String symbol;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return symbol;
    }
}