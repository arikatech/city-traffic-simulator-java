
package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.rules.actions.*;
import com.smartcity.simulator.rules.conditions.*;
import com.smartcity.simulator.signals.SignalState;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.vehicles.*;
import com.smartcity.simulator.signals.TrafficLight;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Stage56FinalVerificationTest {

    private static int checks = 0;
    private static final double EPS = 1e-6;

    private static void check(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static void near(double actual, double expected,
            String message) {

        check(Math.abs(actual - expected) <= EPS, message
                + ": expected " + expected + ", got " + actual);
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
        } catch (InvalidRuleException expected) {
            checks++;
            return;
        }
        throw new AssertionError(
                "Expected InvalidRuleException");
    }

    // A fresh, reproducible test city.
    private static final class CarFixture {

        final CityMap city = new CityMap();

        final Road entry = new Road("R-IN", 400, 10);
        final Road exit = new Road("R-OUT", 400, 10);

        final Lane entryLane;
        final Lane exitLane;

        final Car car;
        final SimulationClock clock = new SimulationClock(1);
        final SimulationEngine engine;

        CarFixture() {

            city.addRoad(entry);
            city.addRoad(exit);
            city.connectRoads(entry, exit);

            entryLane = entry.addLane("L-IN");
            exitLane = exit.addLane("L-OUT");

            car = new Car("C-01", entryLane);
            car.assignRoute(new Route(List.of(entry, exit)));
            car.setSpeed(10);

            city.addVehicle(car);

            engine = new SimulationEngine(clock, city);
        }
    }

    public static void main(String[] args) {

        System.out.println("======================================");
        System.out.println(" STAGE 5.6 FINAL RULE ENGINE TEST");
        System.out.println("======================================");

        testCompositionAndSnapshots();
        testMultiTickRuleLifecycle();
        testRoadConflictResolution();
        testCrossRoadSpeedConstraint();
        testSignalSafetyAndFairness();
        testRegistrationValidation();
        testDeterminism();

        System.out.println("======================================");
        System.out.println(
                "STAGE 5.6 PASSED! (" + checks + " checks)");
        System.out.println("======================================");
    }

    // ==========================================
    // TEST 1: COMPOSITION AND SNAPSHOTS
    // ==========================================

    private static void testCompositionAndSnapshots() {

        CarFixture f = new CarFixture();

        SimulationContext dry = SimulationContext.capture(
                f.clock, f.city, Weather.DRY);

        SimulationContext rain = SimulationContext.capture(
                f.clock, f.city, Weather.RAIN);

        Condition<Vehicle> rainyCarWithTraffic =
                new AndCondition<>(
                        new VehicleTypeCondition(
                                EqualityComparison.EQUAL,
                                VehicleFamily.CAR),

                        new AndCondition<>(
                                new WeatherCondition<>(
                                        EqualityComparison.EQUAL,
                                        Weather.RAIN),
                                new VehicleCountCondition(
                                        Comparison.GREATER_OR_EQUAL,
                                        1)
                        )
                );

        Rule<Vehicle> rule = new Rule<>(
                "RAIN",
                "Rain congestion speed cap",
                5,
                true,
                RuleScopes.all(),
                rainyCarWithTraffic,
                new LimitSpeedAction(0.8)
        );

        RuleEngine rules = new RuleEngine(f.city);
        rules.addVehicleRule(rule);

        near(rules.evaluate(rain).resolve()
                        .speedFractionFor("C-01"),
                0.8, "Rain condition");

        near(rules.evaluate(dry).resolve()
                        .speedFractionFor("C-01"),
                1.0, "Dry condition");

        // OR and NOT composition.
        Condition<Vehicle> alternative = new OrCondition<>(
                new VehicleTypeCondition(
                        EqualityComparison.EQUAL,
                        VehicleFamily.BUS),
                new NotCondition<>(
                        new WeatherCondition<>(
                                EqualityComparison.EQUAL,
                                Weather.RAIN))
        );

        check(alternative.evaluate(f.car, dry),
                "OR/NOT failed in dry weather");

        check(!alternative.evaluate(f.car, rain),
                "OR/NOT failed in rain");

        // Verify AND/OR short-circuit evaluation.
        AtomicInteger rightCalls = new AtomicInteger();

        Condition<Vehicle> right = (vehicle, ctx) -> {
            rightCalls.incrementAndGet();
            return true;
        };

        Condition<Vehicle> shortAnd = new AndCondition<>(
                (vehicle, ctx) -> false,
                right
        );

        check(!shortAnd.evaluate(f.car, rain),
                "AND result incorrect");

        check(rightCalls.get() == 0,
                "AND evaluated unnecessary right condition");

        Condition<Vehicle> shortOr = new OrCondition<>(
                (vehicle, ctx) -> true,
                right
        );

        check(shortOr.evaluate(f.car, rain),
                "OR result incorrect");

        check(rightCalls.get() == 0,
                "OR evaluated unnecessary right condition");

        // The captured speed must not change when
        // the real vehicle is modified afterward.
        double observedSpeed = rain.getRuleObservations()
                .vehicle("C-01").speedMetersPerSecond();

        f.car.setSpeed(5);

        near(rain.getRuleObservations()
                        .vehicle("C-01").speedMetersPerSecond(),
                observedSpeed,
                "Snapshot changed after vehicle mutation");

        f.car.setSpeed(10);

        System.out.println(
                "1. Composition and snapshot consistency: PASSED");
    }

    // ==========================================
    // TEST 2: MULTI-TICK RULE LIFECYCLE
    // ==========================================

    private static void testMultiTickRuleLifecycle() {

        CarFixture f = new CarFixture();
        RuleEngine rules = f.engine.getRuleEngine();

        Rule<Vehicle> cap80 = new Rule<>(
                "CAP80", "80% cap", 10, true,
                RuleScopes.all(),
                (v, ctx) -> true,
                new LimitSpeedAction(0.8)
        );

        Rule<Vehicle> cap60 = new Rule<>(
                "CAP60", "60% cap", 1, true,
                RuleScopes.all(),
                (v, ctx) -> true,
                new LimitSpeedAction(0.6)
        );

        // Automatically activates at simulated t = 3.
        Rule<Vehicle> timedStop = new Rule<>(
                "TIMED-STOP", "Timed stop", 5, true,
                RuleScopes.all(),
                new SimulationTimeCondition<>(
                        Comparison.GREATER_OR_EQUAL, 3),
                new StopVehicleAction()
        );

        rules.addVehicleRule(cap80);
        rules.addVehicleRule(cap60);
        rules.addVehicleRule(timedStop);

        // Three ticks before the timed stop.
        for (int i = 0; i < 3; i++) {

            double before = f.car.getPositionMeters();
            f.engine.step();

            double moved =
                    f.car.getPositionMeters() - before;

            check(moved > 0 && moved <= 6.0 + EPS,
                    "Strictest speed cap failed at tick " + i);

            near(f.engine.getLastResolvedRuleEffects()
                            .speedFractionFor("C-01"),
                    0.6, "Speed cap compounded or changed");
        }

        // At t = 3, the time condition activates.
        double before = f.car.getPositionMeters();
        f.engine.step();

        near(f.car.getPositionMeters(), before,
                "Timed stop failed");

        check(f.engine.getLastResolvedRuleEffects()
                        .isStopped("C-01"),
                "Stop did not override lower-priority caps");

        RuleEffects.Resolved stoppedSnapshot =
                f.engine.getLastResolvedRuleEffects();

        // Disable stop. Speed restriction should return.
        rules.setEnabled("TIMED-STOP", false);

        before = f.car.getPositionMeters();
        f.engine.step();

        check(f.car.getPositionMeters() > before,
                "Vehicle failed to resume");

        near(f.engine.getLastResolvedRuleEffects()
                        .speedFractionFor("C-01"),
                0.6, "60% cap not restored");

        // Old resolved effects remain unchanged.
        check(stoppedSnapshot.isStopped("C-01"),
                "Previous effect snapshot changed");

        rules.remove("CAP60");
        f.engine.step();

        near(f.engine.getLastResolvedRuleEffects()
                        .speedFractionFor("C-01"),
                0.8, "80% cap not restored");

        rules.remove("CAP80");
        f.engine.step();

        near(f.engine.getLastResolvedRuleEffects()
                        .speedFractionFor("C-01"),
                1.0, "Removed speed cap persisted");

        System.out.println(
                "2. Multi-tick rule lifetime: PASSED");
    }

    // ==========================================
    // TEST 3: ROAD CONFLICTS AND INCIDENTS
    // ==========================================

    private static void testRoadConflictResolution() {

        CarFixture f = new CarFixture();
        RuleEngine rules = f.engine.getRuleEngine();

        Rule<Road> close = new Rule<>(
                "CLOSE", "Close exit", 5, true,
                RuleScopes.road("R-OUT"),
                (road, ctx) -> true,
                RoadEntryAction.close()
        );

        Rule<Road> allow = new Rule<>(
                "ALLOW", "Allow exit", 3, true,
                RuleScopes.road("R-OUT"),
                (road, ctx) -> true,
                RoadEntryAction.allow()
        );

        rules.addRoadRule(close);
        rules.addRoadRule(allow);

        f.engine.step();

        check(!f.exit.isEntryOpen(),
                "Higher-priority closure failed");

        // Equal priority: CLOSE must win.
        rules.replaceRoadRule(new Rule<>(
                "ALLOW", "Equal-priority allow", 5, true,
                RuleScopes.road("R-OUT"),
                (road, ctx) -> true,
                RoadEntryAction.allow()
        ));

        f.engine.step();

        check(!f.exit.isEntryOpen(),
                "Equal priority did not favor closure");

        // Higher-priority ALLOW wins against rule CLOSE.
        rules.replaceRoadRule(new Rule<>(
                "ALLOW", "Higher-priority allow", 10, true,
                RuleScopes.road("R-OUT"),
                (road, ctx) -> true,
                RoadEntryAction.allow()
        ));

        f.engine.step();

        check(f.exit.isEntryOpen(),
                "Higher-priority ALLOW failed");

        // Simulate a separately owned incident reason.
        f.exit.blockEntry("event:accident-test");

        f.engine.step();

        check(!f.exit.isEntryOpen(),
                "ALLOW bypassed incident restriction");

        check(f.exit.getEntryBlockReasons()
                        .contains("event:accident-test"),
                "Rule erased an incident reason");

        f.exit.unblockEntry("event:accident-test");

        f.engine.step();

        check(f.exit.isEntryOpen(),
                "Expired incident did not restore entry");

        // Removing ALLOW leaves CLOSE as the winning rule.
        rules.remove("ALLOW");
        f.engine.step();

        check(!f.exit.isEntryOpen(),
                "Remaining CLOSE rule did not apply");

        // Disable the final closure rule.
        rules.setEnabled("CLOSE", false);
        f.engine.step();

        check(f.exit.isEntryOpen(),
                "Disabled closure persisted");

        rules.remove("CLOSE");
        f.engine.step();

        check(f.exit.isEntryOpen(),
                "Deleted rule left a restriction");

        System.out.println(
                "3. Road conflicts and incident ownership: PASSED");
    }

    // ==========================================
    // TEST 4: CROSS-ROAD SPEED CONSTRAINTS
    // ==========================================

    private static void testCrossRoadSpeedConstraint() {

        CarFixture f = new CarFixture();

        f.car.commitStep(new MovementProposal(
                f.car.getId(),
                0,
                397,
                f.entryLane,
                f.entryLane
        ));

        f.engine.getRuleEngine().addVehicleRule(
                new Rule<>(
                        "BOUNDARY-CAP",
                        "Road boundary speed cap",
                        5,
                        true,
                        RuleScopes.all(),
                        (v, ctx) -> true,
                        new LimitSpeedAction(0.4)
                )
        );

        f.engine.step();

        double travelled;

        if (f.car.getCurrentRoad() == f.entry) {
            travelled =
                    f.car.getPositionMeters() - 397.0;

        } else if (f.car.getCurrentRoad() == f.exit) {
            travelled =
                    (f.entry.getLengthMeters() - 397.0)
                    + f.car.getPositionMeters();

        } else {
            throw new AssertionError(
                    "Vehicle entered an unexpected road");
        }

        check(travelled >= -EPS && travelled <= 4.0 + EPS,
                "Speed cap failed at road transition");

        System.out.println(
                "4. Cross-road speed constraint: PASSED");
    }

    // ==========================================
    // TEST 5: SIGNAL SAFETY AND FAIRNESS
    // ==========================================

    private static void testSignalSafetyAndFairness() {

        CityMap city = new CityMap();

        Road east = new Road("E-IN", 60, 10);
        Road north = new Road("N-IN", 60, 10);

        city.addRoad(east);
        city.addRoad(north);

        east.addLane("E-LANE");
        north.addLane("N-LANE");

        Intersection intersection = new Intersection(
                "I-01",
                List.of(east, north),
                4, 2, 2
        );

        city.addIntersection(intersection);

        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(1), city);

        Rule<TrafficLight> priorityRule = new Rule<>(
                "NORTH-GREEN",
                "Favor north",
                10,
                true,
                RuleScopes.lightsOnRoad("N-IN"),
                (light, ctx) -> true,
                new RequestSignalPhaseAction(
                        SignalState.GREEN)
        );

        engine.getRuleEngine()
                .addTrafficLightRule(priorityRule);

        // First tick: clearance must remain RED.
        engine.step();

        check(intersection.getController()
                        .getSignalFor(north).getState()
                        == SignalState.RED,
                "Signal skipped initial clearance");

        check("N-IN".equals(
                intersection.getController()
                        .getRequestedPriorityRoadId()),
                "Signal request was not delivered");

        SimulationContext firstSnapshot =
                engine.getLastTickContext();

        // Second tick: first safe GREEN can be selected.
        engine.step();

        check(intersection.getController()
                        .getSignalFor(north).getState()
                        == SignalState.GREEN,
                "Requested north GREEN was not scheduled");

        check(firstSnapshot.getRuleObservations()
                        .signalStateForRoad("N-IN")
                        == SignalState.RED,
                "Old signal snapshot was modified");

        // Continue requesting NORTH priority.
        // EAST must still eventually get a turn.
        boolean eastReceivedGreen = false;

        for (int i = 0; i < 24; i++) {

            engine.step();

            SignalState eastState =
                    intersection.getController()
                            .getSignalFor(east).getState();

            SignalState northState =
                    intersection.getController()
                            .getSignalFor(north).getState();

            check(!(eastState == SignalState.GREEN
                            && northState == SignalState.GREEN),
                    "Conflicting simultaneous green signals");

            if (eastState == SignalState.GREEN) {
                eastReceivedGreen = true;
            }
        }

        check(eastReceivedGreen,
                "Priority requests starved the other approach");

        System.out.println(
                "5. Signal clearance and fairness: PASSED");
    }

    // ==========================================
    // TEST 6: INVALID RULE REGISTRATION
    // ==========================================

    private static void testRegistrationValidation() {

        CarFixture f = new CarFixture();
        RuleEngine rules = f.engine.getRuleEngine();

        Rule<Vehicle> valid = new Rule<>(
                "KEEP", "Original valid rule", 1, true,
                RuleScopes.all(),
                (v, ctx) -> true,
                new StopVehicleAction()
        );

        rules.addVehicleRule(valid);

        // Duplicate IDs are forbidden across target types.
        expectInvalid(() -> rules.addRoadRule(
                new Rule<>(
                        "KEEP", "Duplicate ID", 1, true,
                        RuleScopes.road("R-IN"),
                        (r, ctx) -> true,
                        RoadEntryAction.close()
                )
        ));

        // Invalid replacement must not destroy valid rule.
        expectInvalid(() -> rules.replaceVehicleRule(
                new Rule<>(
                        "KEEP", "Invalid replacement", 1, true,
                        RuleScopes.vehiclesOnRoad("UNKNOWN"),
                        (v, ctx) -> true,
                        new StopVehicleAction()
                )
        ));

        expectInvalid(() -> rules.setEnabled(
                "DOES-NOT-EXIST", false));

        expectInvalid(() ->
                new LimitSpeedAction(Double.NaN));

        expectInvalid(() ->
                new LimitSpeedAction(-0.1));

        expectInvalid(() ->
                new RoadEntryAction(null));

        check(rules.getVehicleRules().size() == 1,
                "Invalid registration modified rule count");

        check(rules.getVehicleRules().get(0) == valid,
                "Invalid replacement changed existing rule");

        // Exposed collection must not be writable.
        boolean immutable = false;

        try {
            rules.getVehicleRules().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }

        check(immutable, "Rule collection is mutable externally");

        System.out.println(
                "6. Validation and registration integrity: PASSED");
    }

    // ==========================================
    // TEST 7: DETERMINISTIC REPLAY
    // ==========================================

    private static List<Double> replay() {

        CarFixture f = new CarFixture();

        f.engine.getRuleEngine().addVehicleRule(
                new Rule<>(
                        "REPLAY-CAP", "Deterministic cap",
                        5, true,
                        RuleScopes.all(),
                        (v, ctx) -> true,
                        new LimitSpeedAction(0.7)
                )
        );

        List<Double> positions = new ArrayList<>();

        for (int tick = 0; tick < 10; tick++) {
            f.engine.step();
            positions.add(f.car.getPositionMeters());
        }

        return List.copyOf(positions);
    }

    private static void testDeterminism() {

        List<Double> first = replay();
        List<Double> second = replay();

        check(first.equals(second),
                "Identical scenarios produced different results");

        check(first.size() == 10,
                "Unexpected number of replay samples");

        System.out.println(
                "7. Deterministic replay: PASSED");
    }
}
