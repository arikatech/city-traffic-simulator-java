package com.smartcity.simulator.rules.builder;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.*;
import com.smartcity.simulator.rules.actions.*;
import com.smartcity.simulator.rules.conditions.*;
import com.smartcity.simulator.signals.SignalState;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.Objects;
import java.util.function.Function;

/** Translates a RuleDraft into a typed, unregistered Rule object. */
public final class RuleBuilder {

    private final CityMap city;
    private final RuleExtensionRegistry extensions;

    public RuleBuilder(CityMap city) {
        this(city, new RuleExtensionRegistry());
    }

    public RuleBuilder(CityMap city, RuleExtensionRegistry extensions) {
        this.city = Objects.requireNonNull(city, "city");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
    }

    // Neither construction nor validation changes the RuleEngine.
    public CompiledRule build(String id, RuleDraft draft) {
        validateShape(id, draft);

        String cleanId = id.strip();
        String name = draft.name().strip();
        int priority = RuleValueParser.integer(draft.priority(), "Priority");

        return switch (draft.target()) {
            case VEHICLE -> new CompiledRule.VehicleEntry(
                    new Rule<>(cleanId, name, priority, draft.enabled(),
                            vehicleScope(draft),
                            combine(draft, this::vehicleCondition),
                            vehicleAction(draft.action())));

            case PEDESTRIAN -> new CompiledRule.PedestrianEntry(
                    new Rule<>(cleanId, name, priority, draft.enabled(),
                            pedestrianScope(draft),
                            combine(draft, this::pedestrianCondition),
                            pedestrianAction(draft.action())));

            case TRAFFIC_LIGHT -> new CompiledRule.LightEntry(
                    new Rule<>(cleanId, name, priority, draft.enabled(),
                            lightScope(draft),
                            combine(draft, this::lightCondition),
                            lightAction(draft.action())));

            case ROAD -> new CompiledRule.RoadEntry(
                    new Rule<>(cleanId, name, priority, draft.enabled(),
                            roadScope(draft),
                            combine(draft, this::roadCondition),
                            roadAction(draft.action())));
        };
    }

    private void validateShape(String id, RuleDraft draft) {
        RuleValueParser.required(id, "Rule ID");
        if (draft == null) {
            throw new InvalidRuleException("A rule draft is required.");
        }
        RuleValueParser.required(draft.name(), "Rule name");
        if (draft.target() == null) {
            throw new InvalidRuleException("Choose a target category.");
        }
        if (draft.scope() == null || !draft.scope().supports(draft.target())) {
            throw new InvalidRuleException("Scope is not available for " + draft.target() + ".");
        }
        if (draft.scope().needsId()) {
            String scopeId = RuleValueParser.required(draft.scopeId(), "Scope ID");
            if (!RuleCatalogue.scopeIds(city, draft.scope()).contains(scopeId)) {
                throw new InvalidRuleException(
                        "Unknown or unavailable scope ID: " + scopeId + ".");
            }
        } else {
            RuleValueParser.noValue(draft.scopeId(), "Unrestricted scope");
        }
        if (draft.firstCondition() == null) {
            throw new InvalidRuleException("Choose the first condition.");
        }
        if (draft.secondCondition() != null && draft.connector() == null) {
            throw new InvalidRuleException("Choose AND or OR for two conditions.");
        }
        if (draft.action() == null || draft.action().action() == null) {
            throw new InvalidRuleException("Choose an action.");
        }
    }

    // Generic composition works for Vehicle, Pedestrian, TrafficLight and Road.
    private <T> Condition<T> combine(
            RuleDraft draft,
            Function<RuleDraft.ConditionDraft, Condition<T>> factory) {

        Condition<T> first = negateIfNeeded(
                factory.apply(draft.firstCondition()), draft.firstCondition());

        if (draft.secondCondition() == null) {
            return first;
        }

        Condition<T> second = negateIfNeeded(
                factory.apply(draft.secondCondition()), draft.secondCondition());

        return switch (draft.connector()) {
            case AND -> new AndCondition<>(first, second);
            case OR -> new OrCondition<>(first, second);
        };
    }

    private static <T> Condition<T> negateIfNeeded(
            Condition<T> condition, RuleDraft.ConditionDraft draft) {
        return draft.negated() ? new NotCondition<>(condition) : condition;
    }

    private static void checkCondition(
            RuleDraft.ConditionDraft draft, TargetCategory target) {

        if (draft.condition() == null || !draft.condition().supports(target)) {
            throw new InvalidRuleException(
                    "Condition is not available for " + target + ".");
        }

        if (draft.condition().getValueKind() == ValueKind.PRESENCE) {
            if (draft.operator() != null) {
                throw new InvalidRuleException(
                        "Emergency presence has no comparison operator; use NOT for absence.");
            }
            RuleValueParser.noValue(draft.rawValue(), "Presence condition");
        } else if (!draft.condition().getOperators().contains(draft.operator())) {
            throw new InvalidRuleException(
                    "Unsupported comparison operator for " + draft.condition() + ".");
        }
    }

    private static void checkAction(
            RuleDraft.ActionDraft draft, TargetCategory target) {
        if (draft == null || draft.action() == null
                || !draft.action().supports(target)) {
            throw new InvalidRuleException(
                    "Action is not available for " + target + ".");
        }
        if (draft.action().getValueKind() == ValueKind.NONE) {
            RuleValueParser.noValue(draft.rawValue(), "Selected action");
        }
    }

    private static ConditionOption builtinCondition(ConditionChoice choice) {
        if (choice instanceof ConditionOption builtIn) return builtIn;
        throw new InvalidRuleException("Unregistered condition descriptor: " + choice);
    }

    private static ActionOption builtinAction(ActionChoice choice) {
        if (choice instanceof ActionOption builtIn) return builtIn;
        throw new InvalidRuleException("Unregistered action descriptor: " + choice);
    }

    private static <T> Condition<T> sharedCondition(RuleDraft.ConditionDraft draft) {
        return switch (builtinCondition(draft.condition())) {
            case WEATHER -> new WeatherCondition<>(
                    RuleValueParser.enumOperator(draft.operator()),
                    RuleValueParser.enumValue(
                            draft.rawValue(), Weather.class, "weather"));
            case SIMULATION_TIME -> new SimulationTimeCondition<>(
                    RuleValueParser.numberOperator(draft.operator()),
                    RuleValueParser.nonNegative(draft.rawValue(), "Simulation time"));
            default -> throw new InvalidRuleException(
                    "Not a shared condition: " + draft.condition());
        };
    }

    private Condition<Vehicle> vehicleCondition(RuleDraft.ConditionDraft draft) {
        checkCondition(draft, TargetCategory.VEHICLE);
        if (draft.condition() instanceof RegisteredConditionChoice option) {
            return extensions.buildVehicleCondition(option, draft);
        }
        return switch (builtinCondition(draft.condition())) {
            case WEATHER, SIMULATION_TIME -> sharedCondition(draft);
            case VEHICLE_SPEED -> new VehicleSpeedCondition(
                    RuleValueParser.numberOperator(draft.operator()),
                    RuleValueParser.nonNegative(draft.rawValue(), "Vehicle speed"));
            case VEHICLE_TYPE -> new VehicleTypeCondition(
                    RuleValueParser.enumOperator(draft.operator()),
                    RuleValueParser.enumValue(
                            draft.rawValue(), VehicleFamily.class, "vehicle family"));
            case VEHICLE_COUNT_ON_ROAD -> new VehicleCountCondition(
                    RuleValueParser.numberOperator(draft.operator()),
                    RuleValueParser.count(draft.rawValue(), "Vehicle count"));
            case EMERGENCY_ON_ROAD -> EmergencyPresenceCondition.onVehicleRoad();
            default -> throw new InvalidRuleException("Unsupported vehicle condition.");
        };
    }

    private Condition<Pedestrian> pedestrianCondition(RuleDraft.ConditionDraft draft) {
        checkCondition(draft, TargetCategory.PEDESTRIAN);
        if (draft.condition() instanceof RegisteredConditionChoice option) {
            return extensions.buildPedestrianCondition(option, draft);
        }
        return switch (builtinCondition(draft.condition())) {
            case WEATHER, SIMULATION_TIME -> sharedCondition(draft);
            case PEDESTRIAN_WAITING_TIME -> new PedestrianWaitingCondition(
                    RuleValueParser.numberOperator(draft.operator()),
                    RuleValueParser.nonNegative(draft.rawValue(), "Waiting time"));
            case EMERGENCY_AT_INTERSECTION ->
                EmergencyPresenceCondition.atPedestrianIntersection();
            default -> throw new InvalidRuleException("Unsupported pedestrian condition.");
        };
    }

    private Condition<TrafficLight> lightCondition(RuleDraft.ConditionDraft draft) {
        checkCondition(draft, TargetCategory.TRAFFIC_LIGHT);
        if (draft.condition() instanceof RegisteredConditionChoice option) {
            return extensions.buildLightCondition(option, draft);
        }
        return switch (builtinCondition(draft.condition())) {
            case WEATHER, SIMULATION_TIME -> sharedCondition(draft);
            case EMERGENCY_ON_ROAD -> EmergencyPresenceCondition.onSignalIncomingRoad();
            case EMERGENCY_AT_INTERSECTION -> EmergencyPresenceCondition.atSignalIntersection();
            default -> throw new InvalidRuleException("Unsupported traffic-light condition.");
        };
    }

    private Condition<Road> roadCondition(RuleDraft.ConditionDraft draft) {
        checkCondition(draft, TargetCategory.ROAD);
        if (draft.condition() instanceof RegisteredConditionChoice option) {
            return extensions.buildRoadCondition(option, draft);
        }
        return switch (builtinCondition(draft.condition())) {
            case WEATHER, SIMULATION_TIME -> sharedCondition(draft);
            case ROAD_VEHICLE_COUNT -> new RoadVehicleCountCondition(
                    RuleValueParser.numberOperator(draft.operator()),
                    RuleValueParser.count(draft.rawValue(), "Road vehicle count"));
            case EMERGENCY_ON_ROAD -> EmergencyPresenceCondition.onRoad();
            default -> throw new InvalidRuleException("Unsupported road condition.");
        };
    }

    private Action<Vehicle> vehicleAction(RuleDraft.ActionDraft draft) {
        checkAction(draft, TargetCategory.VEHICLE);
        if (draft.action() instanceof RegisteredActionChoice option) {
            return extensions.buildVehicleAction(option, draft);
        }
        return switch (builtinAction(draft.action())) {
            case LIMIT_SPEED -> new LimitSpeedAction(
                    RuleValueParser.fraction(draft.rawValue()));
            case STOP_VEHICLE -> new StopVehicleAction();
            case REQUEST_VEHICLE_PRIORITY -> new RequestVehiclePriorityAction();
            default -> throw new InvalidRuleException("Unsupported vehicle action.");
        };
    }

    private Action<Pedestrian> pedestrianAction(RuleDraft.ActionDraft draft) {
        checkAction(draft, TargetCategory.PEDESTRIAN);
        if (draft.action() instanceof RegisteredActionChoice option) {
            return extensions.buildPedestrianAction(option, draft);
        }
        return switch (builtinAction(draft.action())) {
            case REQUEST_PEDESTRIAN_PRIORITY -> new RequestPedestrianPriorityAction();
            default -> throw new InvalidRuleException("Unsupported pedestrian action.");
        };
    }

    private Action<TrafficLight> lightAction(RuleDraft.ActionDraft draft) {
        checkAction(draft, TargetCategory.TRAFFIC_LIGHT);
        if (draft.action() instanceof RegisteredActionChoice option) {
            return extensions.buildLightAction(option, draft);
        }
        return switch (builtinAction(draft.action())) {
            case REQUEST_SIGNAL_PHASE -> {
                SignalState phase = RuleValueParser.enumValue(
                        draft.rawValue(), SignalState.class, "signal phase");
                if (phase != SignalState.GREEN && phase != SignalState.RED) {
                    throw new InvalidRuleException(
                            "Signal phase must be GREEN or RED (not YELLOW).");
                }
                yield new RequestSignalPhaseAction(phase);
            }
            default -> throw new InvalidRuleException("Unsupported traffic-light action.");
        };
    }

    private Action<Road> roadAction(RuleDraft.ActionDraft draft) {
        checkAction(draft, TargetCategory.ROAD);
        if (draft.action() instanceof RegisteredActionChoice option) {
            return extensions.buildRoadAction(option, draft);
        }
        return switch (builtinAction(draft.action())) {
            case CLOSE_ROAD_ENTRY -> RoadEntryAction.close();
            case ALLOW_ROAD_ENTRY -> RoadEntryAction.allow();
            default -> throw new InvalidRuleException("Unsupported road action.");
        };
    }

    private TargetScope<Vehicle> vehicleScope(RuleDraft draft) {
        TargetScope<Vehicle> scope = switch (draft.scope()) {
            case ALL -> RuleScopes.all();
            case VEHICLES_ON_ROAD -> RuleScopes.vehiclesOnRoad(draft.scopeId().strip());
            case VEHICLES_APPROACHING_INTERSECTION ->
                RuleScopes.vehiclesApproachingIntersection(draft.scopeId().strip());
            default -> throw new InvalidRuleException("Unsupported vehicle scope.");
        };
        scope.validate(city);
        return scope;
    }

    private TargetScope<Pedestrian> pedestrianScope(RuleDraft draft) {
        TargetScope<Pedestrian> scope = switch (draft.scope()) {
            case ALL -> RuleScopes.all();
            case PEDESTRIANS_AT_INTERSECTION ->
                RuleScopes.pedestriansAtIntersection(draft.scopeId().strip());
            default -> throw new InvalidRuleException("Unsupported pedestrian scope.");
        };
        scope.validate(city);
        return scope;
    }

    private TargetScope<TrafficLight> lightScope(RuleDraft draft) {
        TargetScope<TrafficLight> scope = switch (draft.scope()) {
            case ALL -> RuleScopes.all();
            case LIGHTS_ON_ROAD -> RuleScopes.lightsOnRoad(draft.scopeId().strip());
            case LIGHTS_AT_INTERSECTION -> RuleScopes.lightsAtIntersection(draft.scopeId().strip());
            default -> throw new InvalidRuleException("Unsupported traffic-light scope.");
        };
        scope.validate(city);
        return scope;
    }

    private TargetScope<Road> roadScope(RuleDraft draft) {
        TargetScope<Road> scope = switch (draft.scope()) {
            case ALL -> RuleScopes.all();
            case ROAD_BY_ID -> RuleScopes.road(draft.scopeId().strip());
            default -> throw new InvalidRuleException("Unsupported road scope.");
        };
        scope.validate(city);
        return scope;
    }
}
