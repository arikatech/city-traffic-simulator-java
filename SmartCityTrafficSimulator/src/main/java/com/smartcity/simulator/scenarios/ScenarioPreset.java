package com.smartcity.simulator.scenarios;

public enum ScenarioPreset {

    NORMAL_TRAFFIC(0.10),
    RUSH_HOUR(0.30),
    RAIN(0.10),
    ROADWORK(0.10),
    ACCIDENT(0.10);
//    SLIPPERY_ROAD(0.10); // 0.10 is the authomatic vehicle spawning rate

    private final double carsPerSecond;

    ScenarioPreset(double carsPerSecond) {
        this.carsPerSecond = carsPerSecond;
    }

    public double getCarsPerSecond() {
        return carsPerSecond;
    }
}