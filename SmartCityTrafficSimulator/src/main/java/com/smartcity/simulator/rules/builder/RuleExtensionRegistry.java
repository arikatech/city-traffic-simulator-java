package com.smartcity.simulator.rules.builder;

import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.Action;
import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.rules.conditions.Comparison;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Registration point for additional typed conditions/actions used by RuleBuilderPanel. */
public final class RuleExtensionRegistry {
    private final Map<String, RegisteredConditionChoice> conditionNames = new LinkedHashMap<>();
    private final Map<String, RegisteredActionChoice> actionNames = new LinkedHashMap<>();
    private final Map<RegisteredConditionChoice, Function<RuleDraft.ConditionDraft, Condition<Vehicle>>> vehicleConditions = new LinkedHashMap<>();
    private final Map<RegisteredActionChoice, Function<RuleDraft.ActionDraft, Action<Vehicle>>> vehicleActions = new LinkedHashMap<>();
    private final Map<RegisteredConditionChoice, Function<RuleDraft.ConditionDraft, Condition<Pedestrian>>> pedestrianConditions = new LinkedHashMap<>();
    private final Map<RegisteredActionChoice, Function<RuleDraft.ActionDraft, Action<Pedestrian>>> pedestrianActions = new LinkedHashMap<>();
    private final Map<RegisteredConditionChoice, Function<RuleDraft.ConditionDraft, Condition<TrafficLight>>> lightConditions = new LinkedHashMap<>();
    private final Map<RegisteredActionChoice, Function<RuleDraft.ActionDraft, Action<TrafficLight>>> lightActions = new LinkedHashMap<>();
    private final Map<RegisteredConditionChoice, Function<RuleDraft.ConditionDraft, Condition<Road>>> roadConditions = new LinkedHashMap<>();
    private final Map<RegisteredActionChoice, Function<RuleDraft.ActionDraft, Action<Road>>> roadActions = new LinkedHashMap<>();

    private RegisteredConditionChoice condition(String id, String label,
            TargetCategory target, ValueKind kind) {
        RegisteredConditionChoice option = new RegisteredConditionChoice(id, label, target, kind);
        if (conditionNames.putIfAbsent(id, option) != null) {
            throw new IllegalArgumentException("Duplicate condition plugin id: " + id);
        }
        return option;
    }

    private RegisteredActionChoice action(String id, String label,
            TargetCategory target, ValueKind kind) {
        RegisteredActionChoice option = new RegisteredActionChoice(id, label, target, kind);
        if (actionNames.putIfAbsent(id, option) != null) {
            throw new IllegalArgumentException("Duplicate action plugin id: " + id);
        }
        return option;
    }

    public RegisteredConditionChoice registerVehicleCondition(String id, String label,
            ValueKind kind, Function<RuleDraft.ConditionDraft, Condition<Vehicle>> factory) {
        Objects.requireNonNull(factory);
        RegisteredConditionChoice choice = condition(id, label, TargetCategory.VEHICLE, kind);
        vehicleConditions.put(choice, factory);
        return choice;
    }

    public RegisteredActionChoice registerVehicleAction(String id, String label,
            ValueKind kind, Function<RuleDraft.ActionDraft, Action<Vehicle>> factory) {
        Objects.requireNonNull(factory);
        RegisteredActionChoice choice = action(id, label, TargetCategory.VEHICLE, kind);
        vehicleActions.put(choice, factory);
        return choice;
    }

    Condition<Vehicle> buildVehicleCondition(RegisteredConditionChoice option,
            RuleDraft.ConditionDraft draft) {
        Function<RuleDraft.ConditionDraft, Condition<Vehicle>> factory = vehicleConditions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown vehicle condition plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Condition factory returned null");
    }

    Action<Vehicle> buildVehicleAction(RegisteredActionChoice option,
            RuleDraft.ActionDraft draft) {
        Function<RuleDraft.ActionDraft, Action<Vehicle>> factory = vehicleActions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown vehicle action plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Action factory returned null");
    }

    public RegisteredConditionChoice registerPedestrianCondition(String id, String label,
            ValueKind kind, Function<RuleDraft.ConditionDraft, Condition<Pedestrian>> factory) {
        Objects.requireNonNull(factory);
        RegisteredConditionChoice choice = condition(id, label, TargetCategory.PEDESTRIAN, kind);
        pedestrianConditions.put(choice, factory);
        return choice;
    }

    public RegisteredActionChoice registerPedestrianAction(String id, String label,
            ValueKind kind, Function<RuleDraft.ActionDraft, Action<Pedestrian>> factory) {
        Objects.requireNonNull(factory);
        RegisteredActionChoice choice = action(id, label, TargetCategory.PEDESTRIAN, kind);
        pedestrianActions.put(choice, factory);
        return choice;
    }

    Condition<Pedestrian> buildPedestrianCondition(RegisteredConditionChoice option,
            RuleDraft.ConditionDraft draft) {
        Function<RuleDraft.ConditionDraft, Condition<Pedestrian>> factory = pedestrianConditions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown pedestrian condition plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Condition factory returned null");
    }

    Action<Pedestrian> buildPedestrianAction(RegisteredActionChoice option,
            RuleDraft.ActionDraft draft) {
        Function<RuleDraft.ActionDraft, Action<Pedestrian>> factory = pedestrianActions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown pedestrian action plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Action factory returned null");
    }

    public RegisteredConditionChoice registerLightCondition(String id, String label,
            ValueKind kind, Function<RuleDraft.ConditionDraft, Condition<TrafficLight>> factory) {
        Objects.requireNonNull(factory);
        RegisteredConditionChoice choice = condition(id, label, TargetCategory.TRAFFIC_LIGHT, kind);
        lightConditions.put(choice, factory);
        return choice;
    }

    public RegisteredActionChoice registerLightAction(String id, String label,
            ValueKind kind, Function<RuleDraft.ActionDraft, Action<TrafficLight>> factory) {
        Objects.requireNonNull(factory);
        RegisteredActionChoice choice = action(id, label, TargetCategory.TRAFFIC_LIGHT, kind);
        lightActions.put(choice, factory);
        return choice;
    }

    Condition<TrafficLight> buildLightCondition(RegisteredConditionChoice option,
            RuleDraft.ConditionDraft draft) {
        Function<RuleDraft.ConditionDraft, Condition<TrafficLight>> factory = lightConditions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown light condition plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Condition factory returned null");
    }

    Action<TrafficLight> buildLightAction(RegisteredActionChoice option,
            RuleDraft.ActionDraft draft) {
        Function<RuleDraft.ActionDraft, Action<TrafficLight>> factory = lightActions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown light action plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Action factory returned null");
    }

    public RegisteredConditionChoice registerRoadCondition(String id, String label,
            ValueKind kind, Function<RuleDraft.ConditionDraft, Condition<Road>> factory) {
        Objects.requireNonNull(factory);
        RegisteredConditionChoice choice = condition(id, label, TargetCategory.ROAD, kind);
        roadConditions.put(choice, factory);
        return choice;
    }

    public RegisteredActionChoice registerRoadAction(String id, String label,
            ValueKind kind, Function<RuleDraft.ActionDraft, Action<Road>> factory) {
        Objects.requireNonNull(factory);
        RegisteredActionChoice choice = action(id, label, TargetCategory.ROAD, kind);
        roadActions.put(choice, factory);
        return choice;
    }

    Condition<Road> buildRoadCondition(RegisteredConditionChoice option,
            RuleDraft.ConditionDraft draft) {
        Function<RuleDraft.ConditionDraft, Condition<Road>> factory = roadConditions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown road condition plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Condition factory returned null");
    }

    Action<Road> buildRoadAction(RegisteredActionChoice option,
            RuleDraft.ActionDraft draft) {
        Function<RuleDraft.ActionDraft, Action<Road>> factory = roadActions.get(option);
        if (factory == null) throw new InvalidRuleException("Unknown road action plugin: " + option);
        return Objects.requireNonNull(factory.apply(draft), "Action factory returned null");
    }

    public List<ConditionChoice> conditionsFor(TargetCategory target) {
        Objects.requireNonNull(target);
        List<ConditionChoice> result = new ArrayList<>(RuleCatalogue.conditionsFor(target));
        conditionNames.values().stream().filter(option -> option.supports(target)).forEach(result::add);
        return List.copyOf(result);
    }

    public List<ActionChoice> actionsFor(TargetCategory target) {
        Objects.requireNonNull(target);
        List<ActionChoice> result = new ArrayList<>(RuleCatalogue.actionsFor(target));
        actionNames.values().stream().filter(option -> option.supports(target)).forEach(result::add);
        return List.copyOf(result);
    }

    /** Conversion helper for plugin factories; uses the same validation as built-in rules. */
    public static Comparison numericComparison(RuleOperator operator) {
        return RuleValueParser.numberOperator(operator);
    }

    public static double nonNegative(String value, String label) {
        return RuleValueParser.nonNegative(value, label);
    }

    public static int nonNegativeInteger(String value, String label) {
        return RuleValueParser.count(value, label);
    }

    public static double speedFraction(String value) {
        return RuleValueParser.fraction(value);
    }
}
