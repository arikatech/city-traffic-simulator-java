package com.smartcity.simulator.rules.builder;

import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.rules.conditions.Comparison;
import com.smartcity.simulator.rules.conditions.EqualityComparison;
import java.util.Locale;

// Package-private: parsing belongs to the builder, not to Swing.
final class RuleValueParser {

    private RuleValueParser() {}

    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRuleException(field + " is required.");
        }
        return value.strip();
    }

    static void noValue(String value, String field) {
        if (value != null && !value.isBlank()) {
            throw new InvalidRuleException(field + " does not accept a value.");
        }
    }

    static int integer(String value, String field) {
        String text = required(value, field);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new InvalidRuleException(
                    field + " must be a whole number within the Java int range.");
        }
    }

    static int count(String value, String field) {
        int result = integer(value, field);
        if (result < 0) {
            throw new InvalidRuleException(field + " must be >= 0.");
        }
        return result;
    }

    static double nonNegative(String value, String field) {
        double result = decimal(value, field);
        if (result < 0) {
            throw new InvalidRuleException(field + " must be >= 0.");
        }
        return result;
    }

    static double fraction(String value) {
        double result = decimal(value, "Speed fraction");
        if (result <= 0 || result > 1) {
            throw new InvalidRuleException(
                    "Speed fraction must be > 0 and <= 1.");
        }
        return result;
    }

    private static double decimal(String value, String field) {
        String text = required(value, field);
        final double parsed;
        try {
            parsed = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new InvalidRuleException(field + " must be a number.");
        }
        if (!Double.isFinite(parsed)) {
            throw new InvalidRuleException(field + " must be finite (not NaN or Infinity).");
        }
        return parsed;
    }

    static <E extends Enum<E>> E enumValue(
            String value, Class<E> enumClass, String field) {
        String text = required(value, field).toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(enumClass, text);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRuleException(
                    "Unsupported " + field + ": " + value + ".");
        }
    }

    static Comparison numberOperator(RuleOperator op) {
        if (op == null) {
            throw new InvalidRuleException("Choose a numeric comparison operator.");
        }
        return switch (op) {
            case LESS_THAN -> Comparison.LESS_THAN;
            case LESS_OR_EQUAL -> Comparison.LESS_OR_EQUAL;
            case EQUAL -> Comparison.EQUAL;
            case GREATER_OR_EQUAL -> Comparison.GREATER_OR_EQUAL;
            case GREATER_THAN -> Comparison.GREATER_THAN;
            case NOT_EQUAL -> throw new InvalidRuleException(
                    "Numeric conditions do not support !=.");
        };
    }

    static EqualityComparison enumOperator(RuleOperator op) {
        if (op == null) {
            throw new InvalidRuleException("Choose an equality comparison operator.");
        }
        return switch (op) {
            case EQUAL -> EqualityComparison.EQUAL;
            case NOT_EQUAL -> EqualityComparison.NOT_EQUAL;
            default -> throw new InvalidRuleException(
                    "Enum conditions support only = or !=.");
        };
    }
}
