package com.smartcity.simulator.scenarios;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Crosswalk;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.routing.RoutePlanner;
import com.smartcity.simulator.simulation.SimulationClock;
import com.smartcity.simulator.simulation.SimulationEngine;
import com.smartcity.simulator.simulation.view.LiveMapSnapshotFactory;
import com.smartcity.simulator.simulation.view.MapSnapshot;
import com.smartcity.simulator.simulation.view.MapSnapshot.IncidentKind;
import com.smartcity.simulator.simulation.view.MapSnapshot.IncidentView;
import com.smartcity.simulator.simulation.view.MapSnapshot.SignalColor;
import com.smartcity.simulator.ui.desktop.DesktopSession;
import com.smartcity.simulator.ui.desktop.SpawnKind;
import com.smartcity.simulator.vehicles.Vehicle;
import com.smartcity.simulator.vehicles.EmergencyVehicle;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds a fresh bounded CITY + ENGINE per scenario selection/reset.
 * Reuses Stage 4's installer and deterministic seeded spawner.
 * It does not replace the original domain physics, rules or scheduler.
 */
public final class ScenarioFactory {
    public static final double FIXED_DT_SECONDS = 0.1;
    public static final long REPRODUCIBLE_SEED = 42L;

    public DesktopSession create(ScenarioPreset preset) {
        Objects.requireNonNull(preset, "preset");
        CityMap city = new CityMap();
        Road r1 = addRoad(city, "R-1", 250, 15, 2);
        Road r2 = addRoad(city, "R-2", 300, 14, 2);
        Road r3 = addRoad(city, "R-3", 310, 12, 1);
        Road r4 = addRoad(city, "R-4", 190, 11, 1);
        Road r5 = addRoad(city, "R-5", 540, 14, 2);
        Road r6 = addRoad(city, "R-6", 225, 11, 1);
        Road r7 = addRoad(city, "R-7", 300, 12, 1);

        // Two connected routes from J-1 to J-3; one may be blocked by an incident.
        city.connectRoads(r1, r2);
        city.connectRoads(r3, r2);
        city.connectRoads(r1, r6);
        city.connectRoads(r3, r6);
        city.connectRoads(r2, r4);
        city.connectRoads(r4, r5);
        city.connectRoads(r6, r7);
        city.connectRoads(r7, r5);

        Intersection j1 = new Intersection("J-1", List.of(r1, r3), 8, 2, 1);
        Intersection j2 = new Intersection("J-2", List.of(r2), 8, 2, 1);
        Intersection j3 = new Intersection("J-3", List.of(r4, r7), 8, 2, 1);
        Intersection j4 = new Intersection("J-4", List.of(r6), 8, 2, 1);
        city.addIntersection(j1);
        city.addIntersection(j2);
        city.addIntersection(j3);
        city.addIntersection(j4);

        // Match the three visible Stage 7 crosswalk markers with REAL Stage 3
        // crosswalk domain objects. Each uses its own intersection controller.
        List<Crosswalk> crosswalks = List.of(
                new Crosswalk("CW-1", 8.0, j1),
                new Crosswalk("CW-2", 8.0, j2),
                new Crosswalk("CW-3", 8.0, j4));

        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(FIXED_DT_SECONDS), city);
        List<SpawnRoute> spawnRoutes = List.of(
                new SpawnRoute(r1, r5), new SpawnRoute(r3, r5));
        // ScenarioInstaller installs the traffic spawner ONCE and schedules events.
        ScenarioInstaller.install(preset, engine, city,
                spawnRoutes, r4, REPRODUCIBLE_SEED);
        // A reproducible, capped flow. Spawning occurs after each committed tick.
        double pedestrianRate = preset == ScenarioPreset.RUSH_HOUR ? 0.24 : 0.10;
        PedestrianSpawner pedestrianSpawner = new PedestrianSpawner(
                city, crosswalks, pedestrianRate,
                REPRODUCIBLE_SEED + 7103L, 60);
        engine.installPedestrianSpawner(pedestrianSpawner);
        return new LiveSession(preset, city, engine, r1, r5, r4,
                pedestrianSpawner,
                Map.of("R-1", j1, "R-2", j2, "R-6", j4, "R-4", j3));
    }

    private static Road addRoad(CityMap city, String id, double metres,
            double speedLimit, int lanes) {
        Road road = new Road(id, metres, speedLimit);
        for (int i = 1; i <= lanes; i++) road.addLane(id + "-L" + i);
        city.addRoad(road);
        return road;
    }

    private static final class LiveSession implements DesktopSession {
        private final ScenarioPreset scenario;
        private final CityMap city;
        private final SimulationEngine engine;
        private final Road spawnEntry;
        private final Road destination;
        private final Road incidentRoad;
        private final Map<String, Intersection> signalOwners;
        private final LiveMapSnapshotFactory mapFactory =
                LiveMapSnapshotFactory.compactCityGeometry();
        private final AtomicInteger nextManualId = new AtomicInteger(1);
        private final List<SpawnKind> supported;
        private final RoutePlanner routePlanner;
        private final PedestrianSpawner pedestrianSpawner;

        LiveSession(ScenarioPreset scenario, CityMap city, SimulationEngine engine,
                Road spawnEntry, Road destination, Road incidentRoad,
                PedestrianSpawner pedestrianSpawner,
                Map<String, Intersection> signalOwners) {
            this.scenario = scenario;
            this.city = city;
            this.engine = engine;
            this.spawnEntry = spawnEntry;
            this.destination = destination;
            this.incidentRoad = incidentRoad;
            this.signalOwners = Map.copyOf(signalOwners);
            this.routePlanner = new RoutePlanner(city);
            this.pedestrianSpawner = Objects.requireNonNull(pedestrianSpawner);
            List<SpawnKind> available = new ArrayList<>();
            for (SpawnKind kind : SpawnKind.values()) {
                if (kind == SpawnKind.PEDESTRIAN || constructorFor(kind) != null) {
                    available.add(kind);
                }
            }
            supported = List.copyOf(available);
        }

        @Override public ScenarioPreset scenario() { return scenario; }
        @Override public CityMap city() { return city; }
        @Override public SimulationEngine engine() { return engine; }
        @Override public double fixedStepSeconds() { return FIXED_DT_SECONDS; }
        @Override public double elapsedSeconds() { return engine.getElapsedSeconds(); }
        @Override public long tickCount() { return engine.getTickCount(); }
        @Override public String weatherLabel() { return String.valueOf(engine.getWeather()); }
        @Override public void step() { engine.step(); }
        @Override public List<SpawnKind> supportedSpawnTypes() { return supported; }

        @Override public MapSnapshot publishMap() {
            Map<String, SignalColor> signalColors = new HashMap<>();
            for (Road road : city.getRoads()) {
                Intersection junction = signalOwners.get(road.getId());
                if (junction != null) {
                    // Controller is the source of truth; never guess a signal phase.
                    String color = String.valueOf(junction.getController().getStateFor(road));
                    try {
                        signalColors.put(road.getId(), SignalColor.valueOf(color));
                    } catch (IllegalArgumentException ignored) {
                        // A clearance/unknown state is not misreported as GREEN.
                    }
                }
            }
            List<IncidentView> incidents = List.of();
            // Stage 4's known incident road: only mark it when blocked in its preset.
            double t = elapsedSeconds();
            if (!incidentRoad.isEntryOpen()) {
                // Match Stage 4's scheduled event windows, not merely a rule closure.
                if (scenario == ScenarioPreset.ACCIDENT && t >= 20.0 && t < 60.1) {
                    incidents = List.of(new IncidentView("SCENARIO-ACCIDENT",
                            IncidentKind.ACCIDENT, incidentRoad.getId()));
                } else if (scenario == ScenarioPreset.ROADWORK && t >= 12.0 && t < 75.1) {
                    incidents = List.of(new IncidentView("SCENARIO-ROADWORK",
                            IncidentKind.ROADWORK, incidentRoad.getId()));
                }
            }
            return mapFactory.capture(city, signalColors, incidents);
        }

        @Override public String spawn(SpawnKind kind) {
            Objects.requireNonNull(kind, "kind");
            if (kind == SpawnKind.PEDESTRIAN) {
                Pedestrian pedestrian = pedestrianSpawner.spawnManual();
                return pedestrian == null
                        ? "All crosswalks occupied: pedestrian spawn skipped safely"
                        : "Spawned Pedestrian " + pedestrian.getId()
                                + " at " + pedestrian.getCrosswalk().getId();
            }
            Constructor<? extends Vehicle> ctor = constructorFor(kind);
            if (ctor == null) throw new IllegalArgumentException("Type not supported: " + kind);
            if (!spawnEntry.isEntryOpen()) return "Entry road is closed: spawn skipped";
            Lane lane = spawnEntry.getLanes().get(0);
            for (var entity : city.getMobileEntities()) {
                if (entity instanceof Vehicle existing && existing.getCurrentLane() == lane
                        && existing.getPositionMeters() < 6.0) {
                    return "Entry lane occupied: spawn skipped safely";
                }
            }
            // RoutePlanner returns a List<Road>; Vehicle requires an immutable Route.
            // A blocked network can produce an empty list, which Route rejects.
            List<Road> plannedRoads = routePlanner.findRoute(spawnEntry, destination);
            if (plannedRoads.isEmpty()) {
                return "No available route from " + spawnEntry.getId()
                        + " to " + destination.getId() + ": spawn skipped";
            }
            Route route = new Route(plannedRoads);
            try {
                Vehicle vehicle = ctor.newInstance("MANUAL-" + nextManualId.getAndIncrement(), lane);
                vehicle.assignRoute(route);
                // Use the same nominal speed as automatically spawned cars.
                // A lower hardcoded manual speed made emergency averages worse
                // even before traffic priorities were considered.
                vehicle.setSpeed(Math.min(10.0,
                        Math.min(vehicle.getPhysicalMaximumSpeed(),
                                 spawnEntry.getBaseSpeedLimit())));
                if (vehicle instanceof EmergencyVehicle emergency) {
                    // Enable the EXISTING Stage 3 safe priority-request flow.
                    // This requests an earlier legal green; it never bypasses
                    // the controller's yellow/clearance/occupancy checks.
                    emergency.setSirenActive(true);
                }
                city.addVehicle(vehicle);
                return "Spawned " + kind + " " + vehicle.getId();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not instantiate " + kind
                        + ": " + failure.getMessage(), failure);
            }
        }

        @SuppressWarnings("unchecked")
        private static Constructor<? extends Vehicle> constructorFor(SpawnKind kind) {
            try {
                Class<?> type = Class.forName(kind.className());
                if (!Vehicle.class.isAssignableFrom(type)) return null;
                return (Constructor<? extends Vehicle>) type.getConstructor(String.class, Lane.class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                return null;
            }
        }
    }
}