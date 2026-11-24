
package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.rules.actions.*;
import com.smartcity.simulator.rules.conditions.*;
import com.smartcity.simulator.rules.RuleEffects.*;
import com.smartcity.simulator.signals.*;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.vehicles.*;

import java.util.List;

public final class Stage54RuleEffectsTest {

    private static int checks = 0;

    private static void check(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static void expectInvalid(Runnable task) {
        try {
            task.run();
        } catch (InvalidRuleException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Invalid action was accepted");
    }

    public static void main(String[] args) {

        System.out.println("====================================");
        System.out.println("   STAGE 5.4 RULE EFFECTS TEST");
        System.out.println("====================================");

        // Build an independent test city.
        CityMap city = new CityMap();

        Road east = new Road("E-IN", 60, 10);
        Road north = new Road("N-IN", 60, 10);

        city.addRoad(east);
        city.addRoad(north);

        Lane eastLane = east.addLane("E-1");
        Lane northLane = north.addLane("N-1");

        Intersection intersection = new Intersection(
                "I-01",
                List.of(east, north),
                4, 2, 2
        );

        city.addIntersection(intersection);

        Car car = new Car("CAR-01", eastLane);
        Ambulance ambulance =
                new Ambulance("AMB-01", northLane);

        car.setSpeed(6);
        ambulance.setSpeed(6);

        city.addVehicle(car);
        city.addVehicle(ambulance);

        Crosswalk crossing = new Crosswalk(
                "CW-01", 6, intersection);

        Pedestrian pedestrian = new Pedestrian(
                "P-01", crossing, 1.5);

        city.addPedestrian(pedestrian);

        SimulationClock clock = new SimulationClock(1);

        SimulationContext rain = SimulationContext.capture(
                clock, city, Weather.RAIN);

        SimulationContext dry = SimulationContext.capture(
                clock, city, Weather.DRY);

        RuleEngine rules = new RuleEngine(city);

        // =====================================
        // TEST 1: SPEED CAP CONFLICTS
        // =====================================

        Rule<Vehicle> cap80 = new Rule<>(
                "CAP-80", "80 percent cap", 5, true,
                RuleScopes.vehiclesOnRoad("E-IN"),
                (vehicle, ctx) -> true,
                new LimitSpeedAction(0.80)
        );

        rules.addVehicleRule(cap80);

        Resolved result = rules.evaluate(rain).resolve();

        check(result.speedFractionFor("CAR-01") == 0.80,
                "80% cap failed");

        check(result.speedFractionFor("AMB-01") == 1.0,
                "Unmatched vehicle affected");

        Rule<Vehicle> cap60 = new Rule<>(
                "CAP-60", "60 percent cap", 1, true,
                RuleScopes.vehiclesOnRoad("E-IN"),
                (vehicle, ctx) -> true,
                new LimitSpeedAction(0.60)
        );

        rules.addVehicleRule(cap60);

        result = rules.evaluate(rain).resolve();

        check(result.speedFractionFor("CAR-01") == 0.60,
                "Strictest cap did not win");

        Rule<Vehicle> stop = new Rule<>(
                "STOP", "Stop car", -100, true,
                RuleScopes.vehiclesOnRoad("E-IN"),
                (vehicle, ctx) -> true,
                new StopVehicleAction()
        );

        rules.addVehicleRule(stop);

        Resolved stopped = rules.evaluate(rain).resolve();

        check(stopped.isStopped("CAR-01"),
                "Stop must override speed caps");

        // Disable stop: next evaluation restores 60% cap.
        rules.setEnabled("STOP", false);

        result = rules.evaluate(rain).resolve();

        check(result.speedFractionFor("CAR-01") == 0.60,
                "Disabled stop remained effective");

        check(stopped.isStopped("CAR-01"),
                "Previous resolved snapshot changed");

        rules.setEnabled("CAP-60", false);

        check(rules.evaluate(rain).resolve()
                        .speedFractionFor("CAR-01") == 0.80,
                "80% cap not restored");

        rules.setEnabled("CAP-80", false);

        check(rules.evaluate(rain).resolve()
                        .speedFractionFor("CAR-01") == 1.0,
                "Speed cap persisted after disabling rules");

        System.out.println("1. Speed cap and stop conflicts: PASSED");

        // =====================================
        // TEST 2: ROAD CLOSURE CONFLICTS
        // =====================================

        Rule<Road> close = new Rule<>(
                "CLOSE", "Close east road", 5, true,
                RuleScopes.road("E-IN"),
                (road, ctx) -> true,
                RoadEntryAction.close()
        );

        Rule<Road> allow = new Rule<>(
                "ALLOW", "Allow east road", 3, true,
                RuleScopes.road("E-IN"),
                (road, ctx) -> true,
                RoadEntryAction.allow()
        );

        rules.addRoadRule(close);
        rules.addRoadRule(allow);

        result = rules.evaluate(rain).resolve();

        check(result.roadDecisionFor("E-IN")
                        == RoadEntryDecision.CLOSE,
                "Higher-priority closure did not win");

        // Replace allow with a higher priority.
        rules.replaceRoadRule(new Rule<>(
                "ALLOW", "Higher-priority allow", 6, true,
                RuleScopes.road("E-IN"),
                (road, ctx) -> true,
                RoadEntryAction.allow()
        ));

        check(rules.evaluate(rain).resolve()
                        .roadDecisionFor("E-IN")
                        == RoadEntryDecision.ALLOW,
                "Higher-priority allow did not win");

        // Equal priority: CLOSE wins.
        rules.replaceRoadRule(new Rule<>(
                "ALLOW", "Equal-priority allow", 5, true,
                RuleScopes.road("E-IN"),
                (road, ctx) -> true,
                RoadEntryAction.allow()
        ));

        check(rules.evaluate(rain).resolve()
                        .roadDecisionFor("E-IN")
                        == RoadEntryDecision.CLOSE,
                "Equal priority must prefer closure");

        rules.setEnabled("CLOSE", false);

        check(rules.evaluate(rain).resolve()
                        .roadDecisionFor("E-IN")
                        == RoadEntryDecision.ALLOW,
                "Disabling closure failed");

        rules.remove("CLOSE");
        rules.remove("ALLOW");

        check(rules.evaluate(rain).resolve()
                        .roadDecisionFor("E-IN") == null,
                "Road-entry request persisted");

        // Stage 5.4 must not directly mutate roads.
        check(east.isEntryOpen(),
                "Rule action directly mutated road entry");

        System.out.println("2. Road closure resolution: PASSED");

        // =====================================
        // TEST 3: PRIORITY REQUESTS
        // =====================================

        Rule<Vehicle> vehiclePriority = new Rule<>(
                "V-PRIORITY", "Emergency priority", 5, true,
                RuleScopes.vehiclesOnRoad("N-IN"),
                new VehicleTypeCondition(
                        EqualityComparison.EQUAL,
                        VehicleFamily.EMERGENCY),
                new RequestVehiclePriorityAction()
        );

        Rule<Pedestrian> pedestrianPriority = new Rule<>(
                "P-PRIORITY", "Pedestrian priority", 12, true,
                RuleScopes.pedestriansAtIntersection("I-01"),
                new PedestrianWaitingCondition(
                        Comparison.GREATER_OR_EQUAL, 0),
                new RequestPedestrianPriorityAction()
        );

        Rule<TrafficLight> signalPriority = new Rule<>(
                "S-PRIORITY", "Signal priority", 8, true,
                RuleScopes.lightsOnRoad("E-IN"),
                (light, ctx) -> true,
                new RequestSignalPhaseAction(SignalState.GREEN)
        );

        rules.addVehicleRule(vehiclePriority);
        rules.addPedestrianRule(pedestrianPriority);
        rules.addTrafficLightRule(signalPriority);

        SignalState before = intersection.getController()
                .getSignalFor(east).getState();

        result = rules.evaluate(rain).resolve();

        List<PriorityRequest> requests =
                result.requestsAt("I-01");

        check(requests.size() == 3,
                "Expected three priority requests");

        check(requests.get(0).kind()
                        == PriorityKind.PEDESTRIAN,
                "Highest priority request not first");

        check(requests.get(1).kind()
                        == PriorityKind.SIGNAL,
                "Signal priority ordering incorrect");

        check(requests.get(2).kind()
                        == PriorityKind.VEHICLE,
                "Vehicle priority ordering incorrect");

        check(requests.get(1).requestedPhase()
                        == SignalState.GREEN,
                "Signal phase request incorrect");

        check(requests.get(2).subjectId().equals("AMB-01"),
                "Wrong vehicle priority target");

        check(before == intersection.getController()
                        .getSignalFor(east).getState(),
                "Rule action directly changed traffic light");

        System.out.println("3. Ordered priority requests: PASSED");

        // =====================================
        // TEST 4: STABLE TIE-BREAKING
        // =====================================

        Rule<Pedestrian> equalPriority = new Rule<>(
                "P-EQUAL", "Equal priority", 12, true,
                RuleScopes.pedestriansAtIntersection("I-01"),
                (p, ctx) -> true,
                new RequestPedestrianPriorityAction()
        );

        rules.addPedestrianRule(equalPriority);

        requests = rules.evaluate(rain)
                .resolve().requestsAt("I-01");

        check(requests.size() == 4,
                "Expected four priority requests");

        check(requests.get(0).ruleId().equals("P-PRIORITY")
                        && requests.get(1).ruleId().equals("P-EQUAL"),
                "Equal-priority registration order incorrect");

        rules.remove("P-EQUAL");

        System.out.println("4. Stable registration ordering: PASSED");

        // =====================================
        // TEST 5: INVALID ACTION PARAMETERS
        // =====================================

        expectInvalid(() -> new LimitSpeedAction(0));
        expectInvalid(() -> new LimitSpeedAction(1.5));
        expectInvalid(() -> new LimitSpeedAction(Double.NaN));
        expectInvalid(() -> new LimitSpeedAction(
                Double.POSITIVE_INFINITY));

        expectInvalid(() -> new RequestSignalPhaseAction(
                SignalState.YELLOW));

        expectInvalid(() -> new RoadEntryAction(null));

        // Effects cannot be collected without a rule source.
        boolean rejected = false;

        try {
            new RuleEffects().stopVehicle("CAR-01");
        } catch (IllegalStateException expected) {
            rejected = true;
        }

        check(rejected, "Unattributed effect was accepted");

        System.out.println("5. Action validation: PASSED");

        // =====================================
        // TEST 6: FRESH EFFECTS ACROSS TICKS
        // =====================================

        Rule<Vehicle> rainCap = new Rule<>(
                "RAIN-CAP", "Rain speed cap", 7, true,
                RuleScopes.vehiclesOnRoad("E-IN"),
                new WeatherCondition<>(
                        EqualityComparison.EQUAL,
                        Weather.RAIN),
                new LimitSpeedAction(0.40)
        );

        rules.addVehicleRule(rainCap);

        RuleEffects rainyEffects = rules.evaluate(rain);
        RuleEffects dryEffects = rules.evaluate(dry);

        check(rainyEffects != dryEffects,
                "RuleEffects object was reused");

        check(rainyEffects.resolve()
                        .speedFractionFor("CAR-01") == 0.40,
                "Rain cap missing");

        check(dryEffects.resolve()
                        .speedFractionFor("CAR-01") == 1.0,
                "Rain cap persisted into dry weather");

        // Verify no direct modification to the vehicle.
        check(car.getSpeedMetersPerSecond() == 6.0,
                "Action directly changed vehicle speed");

        System.out.println("6. Per-tick effect lifetime: PASSED");

        System.out.println("====================================");
        System.out.println(
                "STAGE 5.4 PASSED! (" + checks + " checks)");
        System.out.println("====================================");
    }
}
