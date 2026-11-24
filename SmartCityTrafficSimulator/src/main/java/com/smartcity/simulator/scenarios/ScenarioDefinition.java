package com.smartcity.simulator.scenarios;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.simulation.SimulationEngine;
import java.util.Objects;

/** An independently definable scenario recipe, without editing ScenarioInstaller. */
public record ScenarioDefinition(
        String id,
        double carsPerSecond,
        boolean requiresIncidentRoad,
        Setup setup) {

    @FunctionalInterface
    public interface Setup {
        void configure(SimulationEngine engine, CityMap city, Road incidentRoad);
    }

    public ScenarioDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Scenario id is required");
        }
        if (!Double.isFinite(carsPerSecond) || carsPerSecond < 0) {
            throw new IllegalArgumentException("Invalid vehicle spawn rate");
        }
        setup = Objects.requireNonNull(setup, "setup");
    }
}
