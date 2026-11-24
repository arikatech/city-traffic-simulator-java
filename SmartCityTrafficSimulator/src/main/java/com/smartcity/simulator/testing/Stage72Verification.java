package com.smartcity.simulator.testing;

import com.smartcity.simulator.ui.desktop.DisplayTickBudget;
import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.simulation.view.LiveMapSnapshotFactory;
import com.smartcity.simulator.simulation.view.MapSnapshot;
import java.util.List;
import java.util.Map;

/** Headless checks of timing and immutable view conversion. No UI display needed. */
public final class Stage72Verification {
    private static int checks;
    private Stage72Verification() { }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable r) {
        boolean failed = false;
        try { r.run(); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Invalid input should be rejected");
    }

    public static void main(String[] args) {
        DisplayTickBudget budget = new DisplayTickBudget(0.1, 12);
        check(budget.claim(0.04, 1.0) == 0, "not a full dt");
        check(budget.claim(0.04, 1.0) == 0, "still not a full dt");
        check(budget.claim(0.02, 1.0) == 1, "one fixed step");
        check(budget.claim(0.10, 2.0) == 2, "display multiplier");
        check(budget.claim(0, 4.0) == 0, "zero wall time");
        budget.reset();
        check(budget.claim(0.05, 1.0) == 0, "reset loses partial credit");
        budget.reset();
        check(budget.claim(1.0, 100.0) == 12, "capped batch");
        budget.reset();
        expectFailure(() -> budget.claim(Double.NaN, 1));
        expectFailure(() -> budget.claim(-1, 1));
        expectFailure(() -> budget.claim(0.1, 0));
        expectFailure(() -> budget.claim(0.1, Double.POSITIVE_INFINITY));
        expectFailure(() -> new DisplayTickBudget(0, 3));
        expectFailure(() -> new DisplayTickBudget(0.1, 0));

        CityMap city = new CityMap();
        Road road = new Road("R-1", 250, 15);
        road.addLane("R-1-L1");
        road.addLane("R-1-L2");
        city.addRoad(road);
        LiveMapSnapshotFactory factory = LiveMapSnapshotFactory.compactCityGeometry();
        MapSnapshot before = factory.capture(city, Map.of(), List.of());
        check(before.roads().size() == 1, "one mapped road");
        check(before.roads().get(0).laneCount() == 2, "real lane count");
        check(before.roads().get(0).lengthMeters() == 250, "real metres");
        check(before.roads().get(0).closed() == false, "road initially open");
        check(before.vehicles().isEmpty(), "no synthetic vehicle positions");
        check(before.signals().isEmpty(), "no invented signals");
        road.blockEntry("test-block");
        MapSnapshot after = factory.capture(city, Map.of(), List.of());
        check(after.roads().get(0).closed(), "committed closure visible");
        check(!before.roads().get(0).closed(), "earlier snapshot immutable");
        check(city.getRoads().size() == 1, "drawing does not alter city");
        boolean immutable = false;
        try { after.roads().clear(); } catch (UnsupportedOperationException ok) {
            immutable = true;
        }
        check(immutable, "rendering view is immutable");
        check(after.mapWidth() == 1000, "map width");
        check(after.mapHeight() == 620, "map height");
        check(after.crosswalks().size() == 3, "crosswalk geometry");
        check(after.intersections().size() == 4, "junction geometry");
        System.out.println("STAGE 7.2 VERIFICATION PASSED! (" + checks + " checks)");
    }
}
