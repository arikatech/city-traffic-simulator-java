package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.scenarios.*;
import com.smartcity.simulator.simulation.*;

import java.util.List;

public final class Stage43Verification {

    private record TestRun(
            SimulationEngine engine,
            TrafficSpawner spawner,
            Road incidentRoad) {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static TestRun createRun(
            ScenarioPreset preset, long seed) {

        CityMap city = new CityMap();

        Road a = new Road("A", 100, 15);
        Road b = new Road("B", 100, 15);
        Road c = new Road("C", 100, 15);
        Road d = new Road("D", 100, 15);
        Road e = new Road("E", 100, 15);

        for (Road road : List.of(a, b, c, d, e)) {
            road.addLane(road.getId() + "-L1");
            city.addRoad(road);
        }

        city.connectRoads(a, b);
        city.connectRoads(a, c);
        city.connectRoads(b, d);
        city.connectRoads(c, d);
        city.connectRoads(e, c);

        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(1.0),
                city
        );

        TrafficSpawner spawner = ScenarioInstaller.install(
                preset,
                engine,
                city,
                List.of(
                        new SpawnRoute(a, d),
                        new SpawnRoute(e, d)
                ),
                b,
                seed
        );

        return new TestRun(engine, spawner, b);
    }

    private static void advance(TestRun run, int ticks) {
        for (int i = 0; i < ticks; i++) {
            run.engine().step();
        }
    }

    public static void main(String[] args) {

        // TEST 1: Rush hour generates more spawn attempts.
        TestRun normal = createRun(
                ScenarioPreset.NORMAL_TRAFFIC, 42L);

        TestRun rush = createRun(
                ScenarioPreset.RUSH_HOUR, 42L);

        advance(normal, 80);
        advance(rush, 80);

        check(
                rush.spawner().getAttemptedCount()
                        > normal.spawner().getAttemptedCount(),
                "Rush hour should attempt more spawns"
        );

        // TEST 2: Identical seeds reproduce spawn history.
        TestRun repeat = createRun(
                ScenarioPreset.RUSH_HOUR, 42L);

        advance(repeat, 80);

        check(
                rush.spawner().getSpawnLog().equals(
                        repeat.spawner().getSpawnLog()),
                "Identical seeds should reproduce spawn history"
        );

        // TEST 3: Rain changes at scheduled times.
        TestRun rain = createRun(
                ScenarioPreset.RAIN, 42L);

        advance(rain, 10);

        check(rain.engine().getWeather() == Weather.DRY,
                "Rain should not begin early");

        rain.engine().step();

        check(rain.engine().getWeather() == Weather.RAIN,
                "Rain should begin at t=10");

        advance(rain, 59);
        rain.engine().step();

        check(rain.engine().getWeather() == Weather.DRY,
                "Rain should end at t=70");

        // TEST 4: Roadwork starts and expires.
        TestRun roadwork = createRun(
                ScenarioPreset.ROADWORK, 42L);

        advance(roadwork, 12);

        check(roadwork.incidentRoad().isEntryOpen(),
                "Roadwork should not begin early");

        roadwork.engine().step();

        check(!roadwork.incidentRoad().isEntryOpen(),
                "Roadwork should close the road");

        advance(roadwork, 62);
        roadwork.engine().step();

        check(roadwork.incidentRoad().isEntryOpen(),
                "Roadwork should expire at t=75");

        System.out.println("Normal attempts: "
                + normal.spawner().getAttemptedCount());

        System.out.println("Rush hour attempts: "
                + rush.spawner().getAttemptedCount());

        System.out.println("Rush hour successful spawns: "
                + rush.spawner().getSpawnedCount());

        System.out.println("Rush hour skipped attempts: "
                + rush.spawner().getSkippedCount());

        System.out.println("STAGE 4.3 PASSED!");
    }
}