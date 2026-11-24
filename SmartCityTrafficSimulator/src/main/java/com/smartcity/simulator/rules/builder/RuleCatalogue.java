package com.smartcity.simulator.rules.builder;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.rules.conditions.VehicleFamily;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.signals.SignalState;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class RuleCatalogue {

    private RuleCatalogue() {
    }

    public static List<ConditionOption> conditionsFor(
            TargetCategory target) {

        Objects.requireNonNull(target, "target");

        return Arrays.stream(ConditionOption.values())
                .filter(option -> option.supports(target))
                .toList();
    }

    public static List<ActionOption> actionsFor(
            TargetCategory target) {

        Objects.requireNonNull(target, "target");

        return Arrays.stream(ActionOption.values())
                .filter(option -> option.supports(target))
                .toList();
    }

    public static List<ScopeOption> scopesFor(
            TargetCategory target) {

        Objects.requireNonNull(target, "target");

        return Arrays.stream(ScopeOption.values())
                .filter(option -> option.supports(target))
                .toList();
    }

    public static List<String> predefinedValues(
            ValueKind kind) {

        Objects.requireNonNull(kind, "kind");

        return switch (kind) {

            case WEATHER ->
                Arrays.stream(Weather.values())
                        .map(Enum::name)
                        .toList();

            case VEHICLE_FAMILY ->
                Arrays.stream(VehicleFamily.values())
                        .filter(family ->
                                family != VehicleFamily.OTHER)
                        .map(Enum::name)
                        .toList();

            case SIGNAL_PHASE -> List.of(
                    SignalState.GREEN.name(),
                    SignalState.RED.name()
            );

            default -> List.of();
        };
    }

    public static List<String> scopeIds(
            CityMap city,
            ScopeOption scope) {

        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(scope, "scope");

        return switch (scope) {

            case ALL -> List.of();

            case VEHICLES_ON_ROAD, ROAD_BY_ID ->
                city.getRoads().stream()
                        .map(Road::getId)
                        .toList();

            case VEHICLES_APPROACHING_INTERSECTION,
                 PEDESTRIANS_AT_INTERSECTION,
                 LIGHTS_AT_INTERSECTION ->
                city.getIntersections().stream()
                        .map(Intersection::getId)
                        .toList();

            case LIGHTS_ON_ROAD ->
                incomingLightRoadIds(city);
        };
    }

    private static List<String> incomingLightRoadIds(
            CityMap city) {

        LinkedHashSet<String> ids = new LinkedHashSet<>();

        for (Intersection intersection :
                city.getIntersections()) {

            if (intersection.getController() == null) {
                continue;
            }

            for (Road incoming :
                    intersection.getController()
                            .getIncomingRoads()) {

                if (intersection.getController()
                        .getSignalFor(incoming)
                        instanceof TrafficLight) {

                    ids.add(incoming.getId());
                }
            }
        }

        return List.copyOf(ids);
    }
}