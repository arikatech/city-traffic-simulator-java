package com.smartcity.simulator.environment;

import java.util.Objects;

public final class EnvironmentState {
    private Weather weather = Weather.DRY;

    public Weather getWeather() {
        return weather;
    }

    public void changeWeather(Weather weather) {
        this.weather = Objects.requireNonNull(weather, "weather");
    }
}