package com.smartcity.simulator.behaviours;

public interface DrivingBehaviour {

    double chooseSpeed(double requestedSpeed, double permittedSpeed);
}