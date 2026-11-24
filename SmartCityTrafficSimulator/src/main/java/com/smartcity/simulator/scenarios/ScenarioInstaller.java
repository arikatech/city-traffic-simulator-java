package com.smartcity.simulator.scenarios;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.simulation.SimulationEngine;
import java.util.List;
import java.util.Objects;

/**
 * Installs a scenario from a recipe instead of branching over event types.
 * Extra scenarios may supply their own ScenarioDefinition without changing this class.
 */
public final class ScenarioInstaller {
    private static final int MAX_SPAWNED_CARS = 50;

    private ScenarioInstaller() {}

    /** Preserves compatibility with the original fixed GUI preset selector. */
    public static TrafficSpawner install(
            ScenarioPreset preset,
            SimulationEngine engine,
            CityMap city,
            List<SpawnRoute> spawnRoutes,
            Road incidentRoad,
            long seed) {
        Objects.requireNonNull(preset, "preset");
        return install(BuiltInScenarios.forPreset(preset), engine, city, spawnRoutes, incidentRoad, seed);
    }

    /** Extension point: caller supplies a new configuration, without editing this class. */
    public static TrafficSpawner install(
            ScenarioDefinition definition,
            SimulationEngine engine,
            CityMap city,
            List<SpawnRoute> spawnRoutes,
            Road incidentRoad,
            long seed) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(spawnRoutes, "spawnRoutes");
        if (definition.requiresIncidentRoad()
                && (incidentRoad == null || !city.getRoads().contains(incidentRoad))) {
            throw new IllegalArgumentException("An incident road in the city is required");
        }

        TrafficSpawner spawner = new TrafficSpawner(
                city, spawnRoutes, definition.carsPerSecond(), seed, MAX_SPAWNED_CARS);
        engine.installTrafficSpawner(spawner);
        definition.setup().configure(engine, city, incidentRoad);
        return spawner;
    }
}
