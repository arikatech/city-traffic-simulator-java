package com.smartcity.simulator.rules.management;

/** One acknowledged command; failure is reported without aborting the simulation tick. */
public record RuleCommandResult(
        String id, String operation, boolean success, String message) {
}
