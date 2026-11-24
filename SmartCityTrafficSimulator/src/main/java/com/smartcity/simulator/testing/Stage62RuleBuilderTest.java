package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.rules.builder.*;
import com.smartcity.simulator.rules.builder.RuleDraft.ActionDraft;
import com.smartcity.simulator.rules.builder.RuleDraft.ConditionDraft;
import com.smartcity.simulator.rules.conditions.VehicleFamily;
import com.smartcity.simulator.signals.SignalState;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.vehicles.*;
import java.util.List;

public final class Stage62RuleBuilderTest {

    private static int checks;

    private static final class Fixture {
        final CityMap city = new CityMap();
        final Road road = new Road("R-1", 100, 10);
        final Road other = new Road("R-2", 100, 10);
        final Car car;
        final Pedestrian pedestrian;
        final SimulationClock clock = new SimulationClock(1);
        final RuleEngine engine;
        final RuleBuilder builder;
        final SimulationContext rain;
        final SimulationContext dry;

        Fixture() {
            city.addRoad(road);
            city.addRoad(other);
            var lane = road.addLane("L-1");
            other.addLane("L-2");
            city.addIntersection(new Intersection(
                    "J-1", List.of(road), 5, 2, 1));
            car = new Car("C-1", lane);
            car.setSpeed(7);
            city.addVehicle(car);
            var crosswalk = new Crosswalk("CW-1", 6, city.getIntersections().get(0));
            pedestrian = new Pedestrian("P-1", crosswalk, 1);
            city.addPedestrian(pedestrian);
            engine = new RuleEngine(city);
            builder = new RuleBuilder(city);
            rain = SimulationContext.capture(clock, city, Weather.RAIN);
            dry = SimulationContext.capture(clock, city, Weather.DRY);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void expectInvalid(String containing, Runnable operation) {
        try {
            operation.run();
        } catch (InvalidRuleException exception) {
            check(exception.getMessage() != null
                    && exception.getMessage().toLowerCase().contains(containing.toLowerCase()),
                    "Wrong error message: " + exception.getMessage());
            return;
        }
        throw new AssertionError("Expected InvalidRuleException: " + containing);
    }

    private static ConditionDraft cond(ConditionOption c, RuleOperator op,
            String value, boolean not) {
        return new ConditionDraft(c, op, value, not);
    }

    private static RuleDraft draft(TargetCategory target, ScopeOption scope,
            String scopeId, ConditionDraft first, ConditionDraft second,
            RuleDraft.Connector connector, ActionOption action,
            String actionValue) {
        return new RuleDraft("My test rule", target, scope, scopeId,
                first, second, connector, new ActionDraft(action, actionValue),
                "5", true);
    }

    public static void main(String[] args) {
        testRealRuleAndBooleanLogic();
        testAllFourCategoriesAndScopes();
        testInvalidInputsAndSafeReplacement();
        System.out.println("STAGE 6.2 PASSED! (" + checks + " checks)");
    }

    private static void testRealRuleAndBooleanLogic() {
        Fixture f = new Fixture();
        RuleDraft rainyCount = draft(TargetCategory.VEHICLE, ScopeOption.ALL,
                null,
                cond(ConditionOption.VEHICLE_COUNT_ON_ROAD,
                        RuleOperator.GREATER_THAN, "0", false),
                cond(ConditionOption.WEATHER, RuleOperator.EQUAL, "RAIN", false),
                RuleDraft.Connector.AND, ActionOption.LIMIT_SPEED, "0.8");

        CompiledRule result = f.builder.build("USER-1", rainyCount);
        check(result instanceof CompiledRule.VehicleEntry, "Must produce typed vehicle rule");
        Rule<Vehicle> vehicleRule = ((CompiledRule.VehicleEntry) result).rule();
        check(vehicleRule.getName().equals("My test rule"), "Name missing");
        check(vehicleRule.getPriority() == 5, "Priority missing");
        check(vehicleRule.isEnabled(), "Enabled missing");
        check(f.engine.getVehicleRules().isEmpty(), "Building must not register a rule");
        f.engine.addVehicleRule(vehicleRule);
        check(f.engine.getVehicleRules().size() == 1, "Registration failed");
        check(f.engine.evaluate(f.rain).resolve().speedFractionFor("C-1") == 0.8,
                "Runtime rainy cap not applied");
        check(f.engine.evaluate(f.dry).resolve().speedFractionFor("C-1") == 1.0,
                "Rain AND condition should be false when dry");

        RuleDraft orNot = draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                cond(ConditionOption.VEHICLE_TYPE, RuleOperator.EQUAL, "BUS", false),
                cond(ConditionOption.WEATHER, RuleOperator.EQUAL, "RAIN", true),
                RuleDraft.Connector.OR, ActionOption.STOP_VEHICLE, null);
        Rule<Vehicle> second = ((CompiledRule.VehicleEntry)
                f.builder.build("USER-2", orNot)).rule();
        f.engine.addVehicleRule(second);
        check(f.engine.evaluate(f.dry).resolve().isStopped("C-1"),
                "OR with NOT should stop a car in dry weather");
        check(!f.engine.evaluate(f.rain).resolve().isStopped("C-1"),
                "OR with NOT should not stop a car in rain");

        RuleDraft singleNot = draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                cond(ConditionOption.WEATHER, RuleOperator.EQUAL, "RAIN", true),
                null, null, ActionOption.LIMIT_SPEED, "0.7");
        Rule<Vehicle> third = ((CompiledRule.VehicleEntry)
                f.builder.build("USER-3", singleNot)).rule();
        check(third.evaluate(f.car, f.dry, new RuleEffects()), "Single NOT should match dry");
        check(!third.evaluate(f.car, f.rain, new RuleEffects()), "Single NOT should not match rain");
    }

    private static void testAllFourCategoriesAndScopes() {
        Fixture f = new Fixture();
        RuleDraft ped = draft(TargetCategory.PEDESTRIAN,
                ScopeOption.PEDESTRIANS_AT_INTERSECTION, "J-1",
                cond(ConditionOption.PEDESTRIAN_WAITING_TIME,
                        RuleOperator.GREATER_OR_EQUAL, "0", false),
                null, null, ActionOption.REQUEST_PEDESTRIAN_PRIORITY, null);
        CompiledRule p = f.builder.build("P-PRIORITY", ped);
        check(p instanceof CompiledRule.PedestrianEntry, "Wrong pedestrian target");
        f.engine.addPedestrianRule(((CompiledRule.PedestrianEntry) p).rule());
        check(f.engine.evaluate(f.rain).resolve().requestsAt("J-1").stream()
                        .anyMatch(req -> req.kind() == RuleEffects.PriorityKind.PEDESTRIAN),
                "Pedestrian action never reached resolver");

        RuleDraft light = draft(TargetCategory.TRAFFIC_LIGHT,
                ScopeOption.LIGHTS_ON_ROAD, "R-1",
                cond(ConditionOption.WEATHER, RuleOperator.EQUAL, "RAIN", false),
                null, null, ActionOption.REQUEST_SIGNAL_PHASE, "GREEN");
        CompiledRule l = f.builder.build("SIGNAL-1", light);
        check(l instanceof CompiledRule.LightEntry, "Wrong light target");
        f.engine.addTrafficLightRule(((CompiledRule.LightEntry) l).rule());
        check(f.engine.evaluate(f.rain).resolve().requestsAt("J-1").stream()
                        .anyMatch(req -> req.kind() == RuleEffects.PriorityKind.SIGNAL
                                && req.requestedPhase() == SignalState.GREEN),
                "Light phase request missing");

        RuleDraft road = draft(TargetCategory.ROAD, ScopeOption.ROAD_BY_ID, "R-1",
                cond(ConditionOption.ROAD_VEHICLE_COUNT,
                        RuleOperator.GREATER_OR_EQUAL, "1", false),
                null, null, ActionOption.CLOSE_ROAD_ENTRY, null);
        CompiledRule r = f.builder.build("ROAD-1", road);
        check(r instanceof CompiledRule.RoadEntry, "Wrong road target");
        f.engine.addRoadRule(((CompiledRule.RoadEntry) r).rule());
        check(f.engine.evaluate(f.rain).resolve().roadDecisionFor("R-1")
                        == RuleEffects.RoadEntryDecision.CLOSE,
                "Road close request missing");
        check(f.engine.evaluate(f.rain).resolve().roadDecisionFor("R-2") == null,
                "Scope must not affect R-2");

        RuleDraft disabled = new RuleDraft("Off", TargetCategory.ROAD,
                ScopeOption.ALL, null,
                cond(ConditionOption.SIMULATION_TIME,
                        RuleOperator.GREATER_OR_EQUAL, "0", false),
                null, null, new ActionDraft(ActionOption.ALLOW_ROAD_ENTRY, null),
                "-2", false);
        Rule<Road> off = ((CompiledRule.RoadEntry)
                f.builder.build("OFF", disabled)).rule();
        check(off.getPriority() == -2 && !off.isEnabled(),
                "Signed priority or enabled state not preserved");
        f.engine.addRoadRule(off);
        check(f.engine.evaluate(f.rain).resolve().roadDecisionFor("R-1")
                        == RuleEffects.RoadEntryDecision.CLOSE,
                "Disabled rule should not compete with closure");
    }

    private static void testInvalidInputsAndSafeReplacement() {
        Fixture f = new Fixture();
        RuleDraft good = draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                cond(ConditionOption.VEHICLE_SPEED, RuleOperator.GREATER_THAN, "2", false),
                null, null, ActionOption.LIMIT_SPEED, "0.9");
        Rule<Vehicle> old = ((CompiledRule.VehicleEntry)
                f.builder.build("EXISTING", good)).rule();
        f.engine.addVehicleRule(old);

        expectInvalid("rule name", () -> f.builder.build("EDIT",
                new RuleDraft("  ", good.target(), good.scope(), good.scopeId(),
                        good.firstCondition(), null, null, good.action(), "5", true)));
        expectInvalid("rule id", () -> f.builder.build("  ", good));
        expectInvalid("target category", () -> f.builder.build("X",
                new RuleDraft("X", null, good.scope(), null,
                        good.firstCondition(), null, null, good.action(), "5", true)));
        expectInvalid("scope", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ROAD_BY_ID, "R-1",
                        good.firstCondition(), null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("scope id", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.VEHICLES_ON_ROAD, "DOES-NOT-EXIST",
                        good.firstCondition(), null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("unrestricted scope", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, "R-1",
                        good.firstCondition(), null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("comparison operator", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.WEATHER, RuleOperator.GREATER_THAN, "RAIN", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("condition", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.PEDESTRIAN_WAITING_TIME, RuleOperator.GREATER_THAN, "0", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("action", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        good.firstCondition(), null, null, ActionOption.CLOSE_ROAD_ENTRY, null)));
        expectInvalid("finite", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.VEHICLE_SPEED, RuleOperator.GREATER_THAN, "NaN", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("finite", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.SIMULATION_TIME, RuleOperator.GREATER_THAN, "Infinity", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("finite", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        good.firstCondition(), null, null, ActionOption.LIMIT_SPEED, "NaN")));
        expectInvalid("speed fraction", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        good.firstCondition(), null, null, ActionOption.LIMIT_SPEED, "1.5")));
        expectInvalid("speed fraction", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        good.firstCondition(), null, null, ActionOption.LIMIT_SPEED, "0")));
        expectInvalid("must be >= 0", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.VEHICLE_COUNT_ON_ROAD, RuleOperator.GREATER_THAN, "-1", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("whole number", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.VEHICLE_COUNT_ON_ROAD, RuleOperator.GREATER_THAN, "1.2", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("whole number", () -> f.builder.build("X",
                new RuleDraft("X", good.target(), good.scope(), null,
                        good.firstCondition(), null, null, good.action(), "2147483648", true)));
        expectInvalid("unsupported vehicle family", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.VEHICLE_TYPE, RuleOperator.EQUAL, "BOAT", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("valid vehicle family", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.VEHICLE_TYPE, RuleOperator.EQUAL, "OTHER", false),
                        null, null, ActionOption.LIMIT_SPEED, "0.5")));
        expectInvalid("signal phase", () -> f.builder.build("X",
                draft(TargetCategory.TRAFFIC_LIGHT, ScopeOption.ALL, null,
                        cond(ConditionOption.WEATHER, RuleOperator.EQUAL, "RAIN", false),
                        null, null, ActionOption.REQUEST_SIGNAL_PHASE, "YELLOW")));
        expectInvalid("does not accept", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        good.firstCondition(), null, null, ActionOption.STOP_VEHICLE, "3")));
        expectInvalid("no comparison operator", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        cond(ConditionOption.EMERGENCY_ON_ROAD, RuleOperator.EQUAL, null, false),
                        null, null, ActionOption.STOP_VEHICLE, null)));
        expectInvalid("choose and or or", () -> f.builder.build("X",
                draft(TargetCategory.VEHICLE, ScopeOption.ALL, null,
                        good.firstCondition(), good.firstCondition(), null,
                        ActionOption.LIMIT_SPEED, "0.8")));

        check(f.engine.getVehicleRules().size() == 1, "Failed edits must not alter registered rules");
        check(f.engine.getVehicleRules().get(0) == old,
                "Failed edits must not replace registered rule instance");
        check(f.engine.evaluate(f.rain).resolve().speedFractionFor("C-1") == 0.9,
                "Failed edits must preserve old rule behavior");
    }
}
