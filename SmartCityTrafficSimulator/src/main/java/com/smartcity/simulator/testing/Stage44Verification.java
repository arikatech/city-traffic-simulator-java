package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.events.*;
import com.smartcity.simulator.routing.*;
import com.smartcity.simulator.scenarios.*;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.vehicles.Car;

import java.util.List;

public final class Stage44Verification {

    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;

        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Road addRoad(CityMap city, String id) {

        Road road = new Road(id, 200.0, 15.0);
        road.addLane(id + "-L1");
        city.addRoad(road);

        return road;
    }

    private static SimulationEngine createEngine(CityMap city) {
        return new SimulationEngine(
                new SimulationClock(1.0),
                city
        );
    }

    private static void testCombinedEvents() {

        CityMap city = new CityMap();

        Road a = addRoad(city, "A");
        Road b = addRoad(city, "B");

        city.connectRoads(a, b);

        Car car = new Car("TEST-CAR", a.getLanes().get(0));
        car.setSpeed(10.0);
        car.assignRoute(new Route(List.of(a, b)));

        city.addVehicle(car);

        SimulationEngine engine = createEngine(city);

        engine.scheduleEvent(
                new RoadworkEvent("RW-01", 1.0, 4.0, b)
        );

        engine.scheduleEvent(
                new AccidentEvent("AC-01", 2.0, 6.0, b)
        );

        engine.scheduleEvent(
                new WeatherChangeEvent(
                        "RAIN-START", 2.0, Weather.RAIN)
        );

        // Tick t=0: Nothing blocked yet.
        engine.step();

        SimulationContext first = engine.getLastTickContext();

        check(first.getWeather() == Weather.DRY,
                "Initial weather should be DRY");

        check(first.isRoadEntryAvailable("B"),
                "Road B initially open");

        check(first.getVehicleCountOnRoad("A") == 1,
                "Snapshot should count the car");

        double beforeClosure = car.getPositionMeters();

        // Tick t=1: Roadwork begins.
        engine.step();

        check(!b.isEntryOpen(),
                "Roadwork must close B");

        check(car.getPositionMeters() == beforeClosure,
                "Car must wait when no route exists");

        // Tick t=2: Accident and rain begin.
        engine.step();

        SimulationContext during =
                engine.getLastTickContext();

        check(during.getWeather() == Weather.RAIN,
                "Rain should begin at t=2");

        check(during.getRoadFacts("B")
                .blockageReasons().size() == 2,
                "Two events must block B independently");

        check(!during.isRoadEntryAvailable("B"),
                "Snapshot must show B closed");

        // Earlier snapshot must remain unchanged.
        check(first.getWeather() == Weather.DRY,
                "Old snapshot weather must not change");

        check(first.isRoadEntryAvailable("B"),
                "Old snapshot road state must not change");

        // Confirm the nested snapshot lists are immutable.
        boolean rejected = false;

        try {
            during.getRoadFacts("B")
                    .blockageReasons()
                    .add("invalid");
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }

        check(rejected,
                "Snapshot collections must be immutable");

        // Advance through t=3 and t=4.
        engine.step();
        engine.step();

        check(b.getEntryBlockReasons().size() == 1,
                "Accident must keep B closed after roadwork");

        // Tick t=5.
        engine.step();

        double beforeReopen = car.getPositionMeters();

        // Tick t=6: Accident expires.
        engine.step();

        check(b.isEntryOpen(),
                "Road B should reopen at t=6");

        check(car.getPositionMeters() > beforeReopen,
                "Car should resume movement after reopening");

        check(engine.getLastTickContext().getWeather()
                == Weather.RAIN,
                "Weather must persist after its event finishes");

        System.out.println("Combined events: PASSED");
    }

    private static void testAutomaticRerouting() {

        CityMap city = new CityMap();

        Road a = addRoad(city, "RA");
        Road b = addRoad(city, "RB");
        Road c = addRoad(city, "RC");
        Road d = addRoad(city, "RD");

        city.connectRoads(a, b);
        city.connectRoads(a, c);
        city.connectRoads(b, d);
        city.connectRoads(c, d);

        Car car = new Car(
                "REROUTE-CAR",
                a.getLanes().get(0)
        );

        car.setSpeed(10.0);

        car.assignRoute(
                new Route(List.of(a, b, d))
        );

        city.addVehicle(car);

        SimulationEngine engine = createEngine(city);

        engine.scheduleEvent(
                new RoadworkEvent("BLOCK-B", 0.0, 5.0, b)
        );

        engine.step();

        check(!b.isEntryOpen(),
                "Road B should be blocked");

        check(car.getNextRoad() == c,
                "Car should reroute through C");

        check(car.getPositionMeters() > 0.0,
                "Rerouted car should continue moving");

        System.out.println("Automatic rerouting: PASSED");
    }

    private static void testLaneRestrictions() {

        CityMap city = new CityMap();

        Road road = addRoad(city, "LROAD");

        Lane firstLane = road.getLanes().get(0);
        Lane secondLane = road.addLane("LROAD-L2");

        SimulationEngine engine = createEngine(city);

        engine.scheduleEvent(
                new RoadworkEvent(
                        "LANE-CLOSURE",
                        0.0,
                        2.0,
                        firstLane
                )
        );

        engine.step();

        SimulationContext context = engine.getLastTickContext();

        check(!context.isLaneEntryAllowed(firstLane.getId()),
                "Blocked lane must deny entry");

        check(context.isLaneEntryAllowed(secondLane.getId()),
                "Other lane should remain open");

        check(context.isRoadEntryAvailable(road.getId()),
                "Road should be usable through its open lane");

        engine.step();
        engine.step();

        check(firstLane.isEntryOpen(),
                "Lane must reopen after event expiration");

        System.out.println("Lane restrictions: PASSED");
    }

    private static List<TrafficSpawner.SpawnRecord>
            runReproducibleScenario(long seed) {

        CityMap city = new CityMap();

        Road a = addRoad(city, "SA");
        Road b = addRoad(city, "SB");
        Road c = addRoad(city, "SC");
        Road d = addRoad(city, "SD");
        Road e = addRoad(city, "SE");
        
        city.connectRoads(e, c);
        city.connectRoads(a, b);
        city.connectRoads(a, c);
        city.connectRoads(b, d);
        city.connectRoads(c, d);

        SimulationEngine engine = createEngine(city);

        TrafficSpawner spawner = ScenarioInstaller.install(
                ScenarioPreset.RUSH_HOUR,
                engine,
                city,
                List.of(
                    new SpawnRoute(a, d),
                    new SpawnRoute(e, d)
                ),
                b,
                seed
        );

        for (int i = 0; i < 30; i++) {
            engine.step();
        }

        check(spawner.getAttemptedCount() == 9,
                "Rush hour should attempt 9 spawns in 30 seconds");

        return spawner.getSpawnLog();
    }

    private static void testRepeatability() {

        List<TrafficSpawner.SpawnRecord> first =
                runReproducibleScenario(42L);

        List<TrafficSpawner.SpawnRecord> second =
                runReproducibleScenario(42L);

        check(first.equals(second),
                "Identical seeds must reproduce spawn history");

        System.out.println("Scenario repeatability: PASSED");
    }

    public static void main(String[] args) {

        testCombinedEvents();
        testAutomaticRerouting();
        testLaneRestrictions();
        testRepeatability();

        System.out.println();
        System.out.println(
                "STAGE 4.4 PASSED! Checks: " + checks
        );
    }
}