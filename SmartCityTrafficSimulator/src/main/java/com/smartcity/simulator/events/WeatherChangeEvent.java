package com.smartcity.simulator.events;

import com.smartcity.simulator.environment.Weather;
import java.util.Objects;

public final class WeatherChangeEvent extends SimulationEvent {
    private final Weather newWeather;

    public WeatherChangeEvent(String id, double atSeconds, Weather newWeather) {
        super(id, atSeconds, atSeconds); // Instantaneous event.
        this.newWeather = Objects.requireNonNull(newWeather, "newWeather");
    }

    public Weather getNewWeather() {
        return newWeather;
    }

    @Override
    protected void onStart(EventContext context) {
        context.environment().changeWeather(newWeather);
    }
}