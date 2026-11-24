package com.smartcity.simulator.behaviours;

public final class DefensiveDriving implements DrivingBehaviour {

    private final double speedFactor;

    public DefensiveDriving(double speedFactor) {
        if (!Double.isFinite(speedFactor)
                || speedFactor <= 0.0
                || speedFactor > 1.0) {

            throw new IllegalArgumentException(
                    "Speed factor must be in (0, 1]."
            );
        }

        this.speedFactor = speedFactor;
    }

    @Override
    public double chooseSpeed(
            double requestedSpeed,
            double permittedSpeed) {

        return Math.min(requestedSpeed, permittedSpeed)
                * speedFactor;
    }
}