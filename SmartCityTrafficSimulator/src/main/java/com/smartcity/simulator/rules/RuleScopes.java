
package com.smartcity.simulator.rules;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class RuleScopes {

    private RuleScopes() {
    }

    // All targets of the requested type.
    public static <T> TargetScope<T> all() {
        return (target, context) -> true;
    }

    // Vehicles currently on a selected road.
    public static TargetScope<Vehicle> vehiclesOnRoad(String roadId) {

        String id = validId(roadId, "road");

        return checked(
                (vehicle, ctx) -> id.equals(
                        ctx.getRuleObservations()
                                .vehicle(vehicle.getId()).roadId()),

                city -> hasRoad(city, id),
                "Unknown road: " + id
        );
    }

    // Vehicles on roads entering a selected intersection.
    public static TargetScope<Vehicle> vehiclesApproachingIntersection(
            String intersectionId) {

        String id = validId(intersectionId, "intersection");

        return checked(
                (vehicle, ctx) -> id.equals(
                        ctx.getRuleObservations()
                                .intersectionForIncomingRoad(
                                        ctx.getRuleObservations()
                                                .vehicle(vehicle.getId())
                                                .roadId())),

                city -> hasIntersection(city, id),
                "Unknown intersection: " + id
        );
    }

    // One specific road.
    public static TargetScope<Road> road(String roadId) {

        String id = validId(roadId, "road");

        return checked(
                (road, ctx) -> id.equals(road.getId()),

                city -> hasRoad(city, id),
                "Unknown road: " + id
        );
    }

    // Pedestrians associated with one intersection.
    public static TargetScope<Pedestrian> pedestriansAtIntersection(
            String intersectionId) {

        String id = validId(intersectionId, "intersection");

        return checked(
                (pedestrian, ctx) -> id.equals(
                        ctx.getRuleObservations()
                                .pedestrian(pedestrian.getId())
                                .intersectionId()),

                city -> hasIntersection(city, id),
                "Unknown intersection: " + id
        );
    }

    // Traffic light controlling one incoming road.
    public static TargetScope<TrafficLight> lightsOnRoad(String roadId) {

        String id = validId(roadId, "road");

        return checked(
                (light, ctx) -> id.equals(
                        light.getIncomingRoad().getId()),

                city -> hasLight(city, id),
                "No traffic light for road: " + id
        );
    }

    // Traffic lights associated with an intersection.
    public static TargetScope<TrafficLight> lightsAtIntersection(
            String intersectionId) {

        String id = validId(intersectionId, "intersection");

        return checked(
                (light, ctx) -> id.equals(
                        ctx.getRuleObservations()
                                .intersectionForIncomingRoad(
                                        light.getIncomingRoad().getId())),

                city -> hasIntersection(city, id),
                "Unknown intersection: " + id
        );
    }

    // Shared implementation for validated scopes.
    private static <T> TargetScope<T> checked(
            BiPredicate<T, SimulationContext> matcher,
            Predicate<CityMap> validLocation,
            String errorMessage) {

        return new TargetScope<>() {

            @Override
            public boolean includes(T target, SimulationContext ctx) {
                return matcher.test(target, ctx);
            }

            @Override
            public void validate(CityMap city) {

                if (!validLocation.test(city)) {
                    throw new InvalidRuleException(errorMessage);
                }
            }
        };
    }

    private static String validId(String value, String kind) {

        if (value == null || value.isBlank()) {
            throw new InvalidRuleException(
                    "Missing " + kind + " ID");
        }

        return value.strip();
    }

    private static boolean hasRoad(CityMap city, String id) {

        return city.getRoads().stream()
                .anyMatch(road -> id.equals(road.getId()));
    }

    private static boolean hasIntersection(CityMap city, String id) {

        return city.getIntersections().stream()
                .anyMatch(intersection ->
                        id.equals(intersection.getId()));
    }

    private static boolean hasLight(CityMap city, String roadId) {

        for (Intersection intersection : city.getIntersections()) {

            if (intersection.getController() == null) {
                continue;
            }

            for (Road incoming :
                    intersection.getController().getIncomingRoads()) {

                if (roadId.equals(incoming.getId())
                        && intersection.getController()
                                .getSignalFor(incoming)
                                instanceof TrafficLight) {

                    return true;
                }
            }
        }

        return false;
    }
}
