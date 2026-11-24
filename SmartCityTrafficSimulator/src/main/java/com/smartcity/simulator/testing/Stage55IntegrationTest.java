
package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.rules.actions.*;
import com.smartcity.simulator.signals.SignalState;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.vehicles.*;

import java.util.List;

public final class Stage55IntegrationTest {

    private static int checks = 0;

    private static void check(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
        checks++;
    }

    public static void main(String[] args) {

        System.out.println("==================================");
        System.out.println("  STAGE 5.5 INTEGRATION TEST");
        System.out.println("==================================");

        testMovementAndRoadRules();
        testSignalPriority();

        System.out.println("==================================");
        System.out.println(
                "STAGE 5.5 PASSED! (" + checks + " checks)");
        System.out.println("==================================");
    }

    private static void testMovementAndRoadRules() {

        CityMap city = new CityMap();

        Road road = new Road("R-01", 200, 10);
        Road exit = new Road("R-02", 200, 10);

        city.addRoad(road);
        city.addRoad(exit);
        city.connectRoads(road, exit);

        Lane lane = road.addLane("L-01");
        exit.addLane("L-02");

        Car car = new Car("C-01", lane);

        car.assignRoute(new Route(List.of(road, exit)));
        car.setSpeed(10);

        city.addVehicle(car);

        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(1), city);

        RuleEngine rules = engine.getRuleEngine();

        // ==================================
        // TEST 1: SPEED CAP
        // ==================================

        Rule<Vehicle> speedRule = new Rule<>(
                "SPEED",
                "Half speed",
                5,
                true,
                RuleScopes.all(),
                (vehicle, ctx) -> true,
                new LimitSpeedAction(0.5)
        );

        rules.addVehicleRule(speedRule);

        double start = car.getPositionMeters();

        engine.step();

        double distance = car.getPositionMeters() - start;

        check(distance > 0 && distance <= 5.000001,
                "50% cap did not restrict actual movement");

        check(engine.getLastResolvedRuleEffects()
                        .speedFractionFor("C-01") == 0.5,
                "Resolved speed fraction was incorrect");

        System.out.println("1. Speed restriction: PASSED");

        // ==================================
        // TEST 2: STOP ACTION
        // ==================================

        Rule<Vehicle> stopRule = new Rule<>(
                "STOP",
                "Stop car",
                1,
                true,
                RuleScopes.all(),
                (vehicle, ctx) -> true,
                new StopVehicleAction()
        );

        rules.addVehicleRule(stopRule);

        start = car.getPositionMeters();

        engine.step();

        check(car.getPositionMeters() == start,
                "Stopped vehicle moved");

        check(engine.getLastResolvedRuleEffects()
                        .isStopped("C-01"),
                "STOP did not override speed cap");

        System.out.println("2. Stop action: PASSED");

        // ==================================
        // TEST 3: DISABLE RULES
        // ==================================

        rules.setEnabled("STOP", false);
        rules.setEnabled("SPEED", false);

        start = car.getPositionMeters();

        engine.step();

        check(car.getPositionMeters() > start,
                "Vehicle did not resume after disabling rules");

        System.out.println("3. Disabled rules: PASSED");

        // ==================================
        // TEST 4: ROAD CLOSURE
        // ==================================

        Rule<Road> close = new Rule<>(
                "CLOSE",
                "Close exit road",
                5,
                true,
                RuleScopes.road("R-02"),
                (r, ctx) -> true,
                RoadEntryAction.close()
        );

        rules.addRoadRule(close);

        engine.step();

        check(!exit.isEntryOpen(),
                "Rule failed to close the road");

        System.out.println("4. Rule road closure: PASSED");

        // ==================================
        // TEST 5: INCIDENT OVERRIDES ALLOW
        // ==================================

        exit.blockEntry("event:manual-test");

        Rule<Road> allow = new Rule<>(
                "ALLOW",
                "Allow exit road",
                10,
                true,
                RuleScopes.road("R-02"),
                (r, ctx) -> true,
                RoadEntryAction.allow()
        );

        rules.addRoadRule(allow);

        engine.step();

        check(!exit.isEntryOpen(),
                "ALLOW incorrectly removed incident closure");

        check(engine.getLastResolvedRuleEffects()
                        .roadDecisionFor("R-02")
                        == RuleEffects.RoadEntryDecision.ALLOW,
                "Higher-priority ALLOW did not win rule conflict");

        System.out.println("5. Incident precedence: PASSED");

        // ==================================
        // TEST 6: RESTORE ROAD
        // ==================================

        exit.unblockEntry("event:manual-test");

        engine.step();

        check(exit.isEntryOpen(),
                "Road did not reopen after incident expired");

        rules.remove("ALLOW");
        rules.remove("CLOSE");

        engine.step();

        check(exit.isEntryOpen(),
                "Removed rules left a road restriction");

        System.out.println("6. Road restoration: PASSED");
    }

    private static void testSignalPriority() {

        CityMap city = new CityMap();

        Road east = new Road("E-IN", 60, 10);
        Road north = new Road("N-IN", 60, 10);

        city.addRoad(east);
        city.addRoad(north);

        east.addLane("E-1");
        north.addLane("N-1");

        Intersection intersection = new Intersection(
                "I-01",
                List.of(east, north),
                4,
                2,
                2
        );

        city.addIntersection(intersection);

        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(1), city);

        RuleEngine rules = engine.getRuleEngine();

        Rule<com.smartcity.simulator.signals.TrafficLight>
                signalRule = new Rule<>(
                        "SIGNAL",
                        "Prioritize north signal",
                        8,
                        true,
                        RuleScopes.lightsOnRoad("N-IN"),
                        (light, ctx) -> true,
                        new RequestSignalPhaseAction(
                                SignalState.GREEN)
                );

        rules.addTrafficLightRule(signalRule);

        engine.step();

        check(engine.getLastResolvedRuleEffects()
                        .requestsAt("I-01").size() == 1,
                "Signal priority request was not collected");

        check("N-IN".equals(
                intersection.getController()
                        .getRequestedPriorityRoadId()),
                "Controller did not receive priority request");

        check(intersection.getController()
                        .getSignalFor(north).getState()
                        == SignalState.RED,
                "Signal skipped its initial RED clearance");

        System.out.println("7. Safe signal priority: PASSED");
    }
}
