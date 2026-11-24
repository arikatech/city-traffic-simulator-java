package com.smartcity.simulator.behaviours;

public final class NormalDriving implements DrivingBehaviour {

    @Override
    public double chooseSpeed(
            double requestedSpeed,
            double permittedSpeed) {

        return Math.min(requestedSpeed, permittedSpeed);
    }
}