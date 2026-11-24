package com.smartcity.simulator.scenarios;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.routing.RoutePlanner;
import com.smartcity.simulator.vehicles.Car;
import com.smartcity.simulator.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class TrafficSpawner {

    private static final double MINIMUM_ENTRY_GAP_METERS = 3.0;
    private static final double EPSILON = 1e-9;

    public record SpawnRecord(
            String vehicleId,
            String entryRoadId,
            String entryLaneId,
            String destinationRoadId) {
    }

    private final CityMap city;
    private final RoutePlanner routePlanner;
    private final List<SpawnRoute> spawnRoutes;
    private final Random random;

    private final double carsPerSecond;
    private final int maxSpawned;

    private final List<SpawnRecord> spawnLog = new ArrayList<>();

    private double spawnBudget = 0.0;

    private int attempted = 0;
    private int spawned = 0;
    private int skipped = 0;

    public TrafficSpawner(
            CityMap city,
            List<SpawnRoute> spawnRoutes,
            double carsPerSecond,
            long seed,
            int maxSpawned) {

        this.city = Objects.requireNonNull(city);
        Objects.requireNonNull(spawnRoutes);

        if (spawnRoutes.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one spawn route is required"
            );
        }

        if (!Double.isFinite(carsPerSecond)
                || carsPerSecond < 0.0
                || carsPerSecond > 1.0) {

            throw new IllegalArgumentException(
                    "Spawn rate must be between 0 and 1 car/sec"
            );
        }

        if (maxSpawned < 0) {
            throw new IllegalArgumentException(
                    "maxSpawned must be non-negative"
            );
        }

        for (SpawnRoute route : spawnRoutes) {
            Objects.requireNonNull(route);

            if (!city.getRoads().contains(route.entryRoad())
                    || !city.getRoads().contains(
                            route.destinationRoad())) {

                throw new IllegalArgumentException(
                        "Spawn roads must belong to CityMap"
                );
            }
        }

        this.spawnRoutes = List.copyOf(spawnRoutes);
        this.routePlanner = new RoutePlanner(city);
        this.random = new Random(seed);
        this.carsPerSecond = carsPerSecond;
        this.maxSpawned = maxSpawned;
    }

    public void advance(double deltaSeconds) {

        if (!Double.isFinite(deltaSeconds)
                || deltaSeconds <= 0.0) {

            throw new IllegalArgumentException(
                    "deltaSeconds must be positive and finite"
            );
        }

        if (spawned >= maxSpawned) {
            return;
        }

        spawnBudget += carsPerSecond * deltaSeconds;

        int due = (int) Math.floor(spawnBudget + EPSILON);

        spawnBudget = Math.max(0.0, spawnBudget - due);

        for (int i = 0; i < due && spawned < maxSpawned; i++) {

            attempted++;

            if (!trySpawn()) {
                skipped++;
            }
        }
    }

    private boolean trySpawn() {

        // Choose a configured entry/destination pair.
        SpawnRoute candidate = spawnRoutes.get(
                random.nextInt(spawnRoutes.size())
        );

        Road entry = candidate.entryRoad();

        if (!entry.hasAvailableEntryLane()) {
            return false;
        }

        // Only consider lanes that accept new entry.
        List<Lane> openLanes = entry.getLanes()
                .stream()
                .filter(Lane::isEntryOpen)
                .toList();

        if (openLanes.isEmpty()) {
            return false;
        }

        Lane lane = openLanes.get(
                random.nextInt(openLanes.size())
        );

        // Reuse Stage 2's closure-aware route planner.
        List<Road> roads = routePlanner.findRoute(
                entry,
                candidate.destinationRoad()
        );

        if (roads.isEmpty()) {
            return false;
        }

        // Create the next uniquely numbered automatic car.
        Car car = new Car("AUTO-" + (spawned + 1), lane);
        car.setSpeed(10.0);

        if (entry.getLengthMeters() < car.getLengthMeters()
                || !isSpawnPositionClear(lane)) {

            return false;
        }

        // Assign a valid route before adding the car.
        car.assignRoute(new Route(roads));

        city.addVehicle(car);

        spawned++;

        spawnLog.add(new SpawnRecord(
                car.getId(),
                entry.getId(),
                lane.getId(),
                candidate.destinationRoad().getId()
        ));

        return true;
    }

    private boolean isSpawnPositionClear(Lane lane) {

        for (MobileEntity entity : city.getMobileEntities()) {

            if (entity instanceof Vehicle vehicle
                    && vehicle.getCurrentLane() == lane) {

                double existingRear = Math.max(
                        0.0,
                        vehicle.getPositionMeters()
                                - vehicle.getLengthMeters()
                );

                if (existingRear < MINIMUM_ENTRY_GAP_METERS) {
                    return false;
                }
            }
        }

        return true;
    }

    public int getAttemptedCount() {
        return attempted;
    }

    public int getSpawnedCount() {
        return spawned;
    }

    public int getSkippedCount() {
        return skipped;
    }

    public List<SpawnRecord> getSpawnLog() {
        return List.copyOf(spawnLog);
    }
}