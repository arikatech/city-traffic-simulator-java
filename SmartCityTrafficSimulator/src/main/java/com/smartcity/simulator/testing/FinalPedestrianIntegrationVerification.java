package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Crosswalk;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.scenarios.PedestrianSpawner;
import com.smartcity.simulator.scenarios.ScenarioPreset;
import com.smartcity.simulator.scenarios.ScenarioFactory;
import com.smartcity.simulator.ui.desktop.DesktopSession;
import com.smartcity.simulator.ui.desktop.SpawnKind;
import java.util.List;

/** End-to-end pedestrian checks against REAL simulation classes, no GUI needed. */
public final class FinalPedestrianIntegrationVerification {
    private static int assertions;
    private FinalPedestrianIntegrationVerification() { }

    private static void check(boolean valid, String description) {
        assertions++;
        if (!valid) throw new AssertionError(description);
    }

    private static void invalid(Runnable action, String description) {
        boolean thrown = false;
        try { action.run(); } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check(thrown, description);
    }

    public static void main(String[] args) {
        DesktopSession normal = new ScenarioFactory().create(ScenarioPreset.NORMAL_TRAFFIC);
        check(normal.supportedSpawnTypes().contains(SpawnKind.PEDESTRIAN), "Pedestrian dropdown option");
        check(normal.publishMap().pedestrians().isEmpty(), "Initially empty pedestrian map");
        check(normal.engine().getStatisticsSnapshot().successfulPedestrianCrossings() == 0,
                "Initially zero crossings");

        String first = normal.spawn(SpawnKind.PEDESTRIAN);
        check(first.contains("MANUAL-PED-1"), "Manual spawn returns domain ID");
        check(normal.publishMap().pedestrians().size() == 1, "Real pedestrian appears on map");
        check(normal.city().getMobileEntities().stream().anyMatch(Pedestrian.class::isInstance),
                "Real pedestrian is in CityMap, not just UI");
        normal.spawn(SpawnKind.PEDESTRIAN);
        normal.spawn(SpawnKind.PEDESTRIAN);
        check(normal.publishMap().pedestrians().size() == 3, "One pedestrian per crosswalk");
        check(normal.spawn(SpawnKind.PEDESTRIAN).contains("skipped safely"),
                "Full crosswalk capacity safely refuses spawn");

        int firstCrossingAt = -1;
        for (int i = 0; i < 1400; i++) {
            normal.step();
            if (firstCrossingAt < 0
                    && normal.engine().getStatisticsSnapshot().successfulPedestrianCrossings() > 0) {
                firstCrossingAt = i;
            }
        }
        check(firstCrossingAt >= 0, "Pedestrian can finish using real signal controller");
        var stats = normal.engine().getStatisticsSnapshot();
        check(stats.successfulPedestrianCrossings() > 0, "Statistics count committed crossings");
        check(stats.pedestrianWaitingSeconds() > 0, "Waiting for signals measured");
        check(normal.publishMap().pedestrians().size() <= 3,
                "Crossing cleanup prevents buildup");
        check(normal.engine().getCompletedTripCount() == stats.completedTrips(),
                "Existing vehicle trip bookkeeping preserved");
        System.out.printf("Normal: t=%.1fs, crossings=%d, pedWait=%.1fs, vehicleTrips=%d%n",
                normal.elapsedSeconds(), stats.successfulPedestrianCrossings(),
                stats.pedestrianWaitingSeconds(), stats.completedTrips());

        // A fresh run with the same seed and commands should be identical.
        DesktopSession again = new ScenarioFactory().create(ScenarioPreset.NORMAL_TRAFFIC);
        again.spawn(SpawnKind.PEDESTRIAN);
        again.spawn(SpawnKind.PEDESTRIAN);
        again.spawn(SpawnKind.PEDESTRIAN);
        for (int i = 0; i < 1400; i++) again.step();
        var same = again.engine().getStatisticsSnapshot();
        check(same.successfulPedestrianCrossings() == stats.successfulPedestrianCrossings(),
                "Crossing totals repeat with same seed");
        check(Math.abs(same.pedestrianWaitingSeconds() - stats.pedestrianWaitingSeconds()) < 1e-8,
                "Waiting totals repeat with same seed");

        DesktopSession rush = new ScenarioFactory().create(ScenarioPreset.RUSH_HOUR);
        for (int i = 0; i < 1000; i++) rush.step();
        check(rush.engine().getStatisticsSnapshot().successfulPedestrianCrossings() > 0,
                "Rush hour automatic pedestrian generation produces crossings");
        check(rush.city().getMobileEntities().stream().filter(Pedestrian.class::isInstance).count() <= 3,
                "Automatic pedestrian generation respects crosswalk capacity");
        System.out.printf("Rush hour: crossings=%d, wait=%.1fs%n",
                rush.engine().getStatisticsSnapshot().successfulPedestrianCrossings(),
                rush.engine().getStatisticsSnapshot().pedestrianWaitingSeconds());

        CityMap tiny = new CityMap();
        Road road = new Road("T", 30, 10);
        tiny.addRoad(road);
        Intersection junction = new Intersection("J", List.of(road), 2, 1, 1);
        tiny.addIntersection(junction);
        Crosswalk crossing = new Crosswalk("CW", 5, junction);
        invalid(() -> new PedestrianSpawner(tiny, List.of(crossing), -1, 42, 4),
                "Negative pedestrian rate rejected");
        invalid(() -> new PedestrianSpawner(tiny, List.of(crossing), 1, 42, -1),
                "Negative pedestrian cap rejected");
        PedestrianSpawner capped = new PedestrianSpawner(tiny, List.of(crossing), 10, 42, 1);
        invalid(() -> capped.advance(0), "Zero dt rejected");
        capped.advance(.1);
        check(capped.getAutomaticSpawnCount() == 1, "Automatic pedestrian created");
        capped.advance(1);
        check(capped.getAutomaticSpawnCount() == 1, "Cap limits automatic spawns");
        check(capped.spawnManual() == null, "Capacity enforced for manual spawn");

        System.out.println("FINAL PEDESTRIAN INTEGRATION PASSED! (" + assertions + " checks)");
    }
}
