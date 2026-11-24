
package com.smartcity.simulator.rules;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.signals.TrafficSignal;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;

import java.util.*;

public final class RuleEngine {

    private enum Category {
        VEHICLE,
        PEDESTRIAN,
        TRAFFIC_LIGHT,
        ROAD
    }

    private final CityMap city;

    private final Map<String, Rule<Vehicle>> vehicleRules =
            new LinkedHashMap<>();

    private final Map<String, Rule<Pedestrian>> pedestrianRules =
            new LinkedHashMap<>();

    private final Map<String, Rule<TrafficLight>> lightRules =
            new LinkedHashMap<>();

    private final Map<String, Rule<Road>> roadRules =
            new LinkedHashMap<>();

    // Keep IDs unique across every target category.
    private final Map<String, Category> categories =
            new HashMap<>();

    // Preserve deterministic registration order.
    private final Map<String, Long> registrationOrder =
            new HashMap<>();

    private long nextOrder;

    public RuleEngine(CityMap city) {
        this.city = Objects.requireNonNull(city);
    }

    // ========================================
    // REGISTRATION
    // ========================================

    public void addVehicleRule(Rule<Vehicle> rule) {
        add(rule, Category.VEHICLE, vehicleRules);
    }

    public void addPedestrianRule(Rule<Pedestrian> rule) {
        add(rule, Category.PEDESTRIAN, pedestrianRules);
    }

    public void addTrafficLightRule(Rule<TrafficLight> rule) {
        add(rule, Category.TRAFFIC_LIGHT, lightRules);
    }

    public void addRoadRule(Rule<Road> rule) {
        add(rule, Category.ROAD, roadRules);
    }

    private <T> void add(
            Rule<T> rule,
            Category category,
            Map<String, Rule<T>> group) {

        Objects.requireNonNull(rule);

        if (categories.containsKey(rule.getId())) {
            throw new InvalidRuleException(
                    "Duplicate rule ID: " + rule.getId());
        }

        rule.getScope().validate(city);

        group.put(rule.getId(), rule);
        categories.put(rule.getId(), category);
        registrationOrder.put(rule.getId(), nextOrder++);
    }

    // ========================================
    // REPLACEMENT
    // ========================================

    public void replaceVehicleRule(Rule<Vehicle> rule) {
        replace(rule, Category.VEHICLE, vehicleRules);
    }

    public void replacePedestrianRule(Rule<Pedestrian> rule) {
        replace(rule, Category.PEDESTRIAN, pedestrianRules);
    }

    public void replaceTrafficLightRule(Rule<TrafficLight> rule) {
        replace(rule, Category.TRAFFIC_LIGHT, lightRules);
    }

    public void replaceRoadRule(Rule<Road> rule) {
        replace(rule, Category.ROAD, roadRules);
    }

    private <T> void replace(
            Rule<T> rule,
            Category category,
            Map<String, Rule<T>> group) {

        Objects.requireNonNull(rule);

        if (categories.get(rule.getId()) != category) {
            throw new InvalidRuleException(
                    "Rule does not exist in this category: "
                            + rule.getId());
        }

        rule.getScope().validate(city);

        // Existing ID and registration order are preserved.
        group.put(rule.getId(), rule);
    }

    // ========================================
    // ATOMIC CROSS-CATEGORY REPLACEMENT (STAGE 6.4)
    // ========================================

    // Validate first; only then move between typed maps. This preserves
    // the rule ID and original registration order even if category changes.
    public void replaceWithVehicleRule(Rule<Vehicle> rule) {
        replaceAcrossCategories(rule, Category.VEHICLE, vehicleRules);
    }

    public void replaceWithPedestrianRule(Rule<Pedestrian> rule) {
        replaceAcrossCategories(rule, Category.PEDESTRIAN, pedestrianRules);
    }

    public void replaceWithTrafficLightRule(Rule<TrafficLight> rule) {
        replaceAcrossCategories(rule, Category.TRAFFIC_LIGHT, lightRules);
    }

    public void replaceWithRoadRule(Rule<Road> rule) {
        replaceAcrossCategories(rule, Category.ROAD, roadRules);
    }

    private <T> void replaceAcrossCategories(
            Rule<T> replacement, Category newCategory,
            Map<String, Rule<T>> targetGroup) {

        Objects.requireNonNull(replacement);
        String id = replacement.getId();
        Category previous = categories.get(id);
        if (previous == null) {
            throw new InvalidRuleException("Unknown rule ID: " + id);
        }

        // May throw. In that case none of our rule registries are changed.
        replacement.getScope().validate(city);

        // Same category: update in place, preserving LinkedHashMap order.
        if (previous == newCategory) {
            targetGroup.put(id, replacement);
            return;
        }

        switch (previous) {
            case VEHICLE -> vehicleRules.remove(id);
            case PEDESTRIAN -> pedestrianRules.remove(id);
            case TRAFFIC_LIGHT -> lightRules.remove(id);
            case ROAD -> roadRules.remove(id);
        }
        targetGroup.put(id, replacement);
        categories.put(id, newCategory);
        // registrationOrder remains unchanged.
    }

    // ========================================
    // ENABLE / DISABLE
    // ========================================

    public void setEnabled(String id, boolean enabled) {

        Category category = categories.get(id);

        if (category == null) {
            throw new InvalidRuleException(
                    "Unknown rule ID: " + id);
        }

        switch (category) {
            case VEHICLE ->
                updateEnabled(vehicleRules, id, enabled);

            case PEDESTRIAN ->
                updateEnabled(pedestrianRules, id, enabled);

            case TRAFFIC_LIGHT ->
                updateEnabled(lightRules, id, enabled);

            case ROAD ->
                updateEnabled(roadRules, id, enabled);
        }
    }

    private static <T> void updateEnabled(
            Map<String, Rule<T>> group,
            String id,
            boolean enabled) {

        group.put(id, group.get(id).withEnabled(enabled));
    }

    // ========================================
    // REMOVAL
    // ========================================

    public boolean remove(String id) {

        Category category = categories.remove(id);

        if (category == null) {
            return false;
        }

        switch (category) {
            case VEHICLE -> vehicleRules.remove(id);
            case PEDESTRIAN -> pedestrianRules.remove(id);
            case TRAFFIC_LIGHT -> lightRules.remove(id);
            case ROAD -> roadRules.remove(id);
        }

        registrationOrder.remove(id);

        return true;
    }

    // ========================================
    // READ-ONLY REGISTERED RULE LISTS
    // ========================================

    public List<Rule<Vehicle>> getVehicleRules() {
        return List.copyOf(vehicleRules.values());
    }

    public List<Rule<Pedestrian>> getPedestrianRules() {
        return List.copyOf(pedestrianRules.values());
    }

    public List<Rule<TrafficLight>> getTrafficLightRules() {
        return List.copyOf(lightRules.values());
    }

    public List<Rule<Road>> getRoadRules() {
        return List.copyOf(roadRules.values());
    }

    // ========================================
    // EVALUATION
    // ========================================

    public RuleEffects evaluate(SimulationContext context) {

        Objects.requireNonNull(context);

        // Fresh effects for each evaluation/tick.
        RuleEffects effects = new RuleEffects();

        List<Vehicle> vehicles = new ArrayList<>();
        List<Pedestrian> pedestrians = new ArrayList<>();

        for (MobileEntity entity : city.getMobileEntities()) {

            if (entity instanceof Vehicle vehicle) {
                vehicles.add(vehicle);

            } else if (entity instanceof Pedestrian pedestrian) {
                pedestrians.add(pedestrian);
            }
        }

        // Collect traffic lights without unsafe casts.
        Map<String, TrafficLight> uniqueLights =
                new LinkedHashMap<>();

        for (Intersection intersection : city.getIntersections()) {

            if (intersection.getController() == null) {
                continue;
            }

            for (Road incoming :
                    intersection.getController().getIncomingRoads()) {

                TrafficSignal signal =
                        intersection.getController()
                                .getSignalFor(incoming);

                if (signal instanceof TrafficLight light) {
                    uniqueLights.putIfAbsent(
                            incoming.getId(), light);
                }
            }
        }

        evaluateGroup(
                vehicleRules,
                vehicles,
                context,
                effects);

        evaluateGroup(
                pedestrianRules,
                pedestrians,
                context,
                effects);

        evaluateGroup(
                lightRules,
                new ArrayList<>(uniqueLights.values()),
                context,
                effects);

        evaluateGroup(
                roadRules,
                new ArrayList<>(city.getRoads()),
                context,
                effects);

        return effects;
    }

    // One generic evaluation algorithm for every target category.
    
    private <T> void evaluateGroup(
            Map<String, Rule<T>> rules,
            List<? extends T> targets,
            SimulationContext ctx,
            RuleEffects effects) {

        List<Rule<T>> sorted =
                new ArrayList<>(rules.values());

        sorted.sort(
                Comparator
                        .comparingInt(
                                (Rule<T> rule) -> rule.getPriority())
                        .reversed()
                        .thenComparingLong(
                                rule -> registrationOrder.get(
                                        rule.getId()))
        );

        for (Rule<T> rule : sorted) {

            // Preserve original registration order
            // for resolving conflicts across all rule types.
            effects.registerRuleOrder(
                    rule.getId(),
                    registrationOrder.get(rule.getId()));

            for (T target : targets) {
                rule.evaluate(target, ctx, effects);
            }
        }
    }

}
