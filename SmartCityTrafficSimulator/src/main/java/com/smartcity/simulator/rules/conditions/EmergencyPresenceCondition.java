
package com.smartcity.simulator.rules.conditions;

import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.Condition;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.simulation.SimulationContext;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.function.BiFunction;

public final class EmergencyPresenceCondition<T>
        implements Condition<T> {

    private enum Scope {
        ROAD,
        INTERSECTION
    }

    private final Scope scope;
    private final BiFunction<T, SimulationContext, String> scopeId;

    private EmergencyPresenceCondition(
            Scope scope,
            BiFunction<T, SimulationContext, String> scopeId) {

        this.scope = scope;
        this.scopeId = scopeId;
    }

    public static EmergencyPresenceCondition<Vehicle>
            onVehicleRoad() {

        return new EmergencyPresenceCondition<>(
                Scope.ROAD,
                (vehicle, ctx) -> ctx.getRuleObservations()
                        .vehicle(vehicle.getId()).roadId());
    }

    public static EmergencyPresenceCondition<Road> onRoad() {

        return new EmergencyPresenceCondition<>(
                Scope.ROAD,
                (road, ctx) -> road.getId());
    }

    public static EmergencyPresenceCondition<Pedestrian>
            atPedestrianIntersection() {

        return new EmergencyPresenceCondition<>(
                Scope.INTERSECTION,
                (pedestrian, ctx) -> ctx.getRuleObservations()
                        .pedestrian(pedestrian.getId())
                        .intersectionId());
    }

    public static EmergencyPresenceCondition<TrafficLight>
            onSignalIncomingRoad() {

        return new EmergencyPresenceCondition<>(
                Scope.ROAD,
                (light, ctx) -> light.getIncomingRoad().getId());
    }

    public static EmergencyPresenceCondition<TrafficLight>
            atSignalIntersection() {

        return new EmergencyPresenceCondition<>(
                Scope.INTERSECTION,
                (light, ctx) -> ctx.getRuleObservations()
                        .intersectionForIncomingRoad(
                                light.getIncomingRoad().getId()));
    }

    @Override
    public boolean evaluate(T target, SimulationContext context) {

        String id = scopeId.apply(target, context);

        return scope == Scope.ROAD
                ? context.getRuleObservations().emergencyOnRoad(id)
                : context.getRuleObservations()
                        .emergencyAtIntersection(id);
    }
}
