package com.smartcity.simulator.ui.desktop;

/**
 * Converts elapsed WALL time into a bounded number of fixed LOGICAL steps.
 * One display-speed unit is one simulated second per wall-clock second.
 * Does not change the simulation clock's fixed dt.
 */
public final class DisplayTickBudget {
    private final double fixedStepSeconds;
    private final int maxStepsPerCallback;
    private double credit;

    public DisplayTickBudget(double fixedStepSeconds, int maxStepsPerCallback) {
        if (!Double.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0) {
            throw new IllegalArgumentException("fixedStepSeconds must be positive");
        }
        if (maxStepsPerCallback < 1) {
            throw new IllegalArgumentException("maxStepsPerCallback must be positive");
        }
        this.fixedStepSeconds = fixedStepSeconds;
        this.maxStepsPerCallback = maxStepsPerCallback;
    }

    /** Called once per timer callback, after observing real elapsed wall time. */
    public int claim(double wallSeconds, double speedMultiplier) {
        if (!Double.isFinite(wallSeconds) || wallSeconds < 0 ||
                !Double.isFinite(speedMultiplier) || speedMultiplier <= 0) {
            throw new IllegalArgumentException("Invalid display clock input");
        }
        // Do not catch up an arbitrarily long UI stall with thousands of ticks.
        double boundedWallTime = Math.min(wallSeconds, 0.25);
        credit = Math.min(maxStepsPerCallback + 1.0,
                credit + boundedWallTime * speedMultiplier / fixedStepSeconds);
        int steps = Math.min(maxStepsPerCallback, (int) Math.floor(credit + 1e-9));
        credit -= steps;
        return steps;
    }

    public void reset() { credit = 0; }
}
