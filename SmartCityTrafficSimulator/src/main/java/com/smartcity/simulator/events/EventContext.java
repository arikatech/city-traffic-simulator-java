package com.smartcity.simulator.events;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.environment.EnvironmentState;
import java.util.Objects;

public record EventContext(CityMap city, EnvironmentState environment) {
    public EventContext {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(environment, "environment");
    }
}