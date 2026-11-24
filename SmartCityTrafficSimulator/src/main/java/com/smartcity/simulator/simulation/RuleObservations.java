
package com.smartcity.simulator.simulation;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.conditions.VehicleFamily;
import com.smartcity.simulator.signals.IntersectionController;
import com.smartcity.simulator.signals.SignalState;
import com.smartcity.simulator.vehicles.Bus;
import com.smartcity.simulator.vehicles.Car;
import com.smartcity.simulator.vehicles.EmergencyVehicle;
import com.smartcity.simulator.vehicles.Truck;
import com.smartcity.simulator.vehicles.Vehicle;
import com.smartcity.simulator.signals.TrafficSignal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RuleObservations {

    // Immutable vehicle observations.
    public record VehicleFacts(
            String roadId,
            double speedMetersPerSecond,
            VehicleFamily family) {

        public VehicleFacts {
            Objects.requireNonNull(family);

            if (!Double.isFinite(speedMetersPerSecond)
                    || speedMetersPerSecond < 0) {
                throw new IllegalArgumentException(
                        "Invalid observed vehicle speed");
            }
        }
    }

    // Immutable pedestrian observations.
    public record PedestrianFacts(
            double waitingSeconds,
            String intersectionId) {

        public PedestrianFacts {
            if (!Double.isFinite(waitingSeconds)
                    || waitingSeconds < 0) {
                throw new IllegalArgumentException(
                        "Invalid pedestrian waiting time");
            }
        }
    }

    private final Map<String, VehicleFacts> vehicles;
    private final Map<String, PedestrianFacts> pedestrians;

    private final Map<String, String> intersectionByIncomingRoad;

    private final Map<String, SignalState> signalStatesByIncomingRoad;

    private final Set<String> emergencyRoads;
    private final Set<String> emergencyIntersections;

    private RuleObservations(
            Map<String, VehicleFacts> vehicles,
            Map<String, PedestrianFacts> pedestrians,
            Map<String, String> intersectionByIncomingRoad,
            Map<String, SignalState> signalStatesByIncomingRoad,
            Set<String> emergencyRoads,
            Set<String> emergencyIntersections) {

        this.vehicles = Map.copyOf(vehicles);

        this.pedestrians = Map.copyOf(pedestrians);

        this.intersectionByIncomingRoad =
                Map.copyOf(intersectionByIncomingRoad);

        this.signalStatesByIncomingRoad =
                Map.copyOf(signalStatesByIncomingRoad);

        this.emergencyRoads = Set.copyOf(emergencyRoads);

        this.emergencyIntersections =
                Set.copyOf(emergencyIntersections);
    }

    public static RuleObservations capture(CityMap city) {

        Objects.requireNonNull(city, "city");

        Map<String, VehicleFacts> vehicles = new HashMap<>();
        Map<String, PedestrianFacts> pedestrians = new HashMap<>();

        Map<String, String> incomingRoadIntersections =
                new HashMap<>();

        Map<String, SignalState> signalStates =
                new HashMap<>();

        Set<String> emergencyRoads = new HashSet<>();
        Set<String> emergencyIntersections = new HashSet<>();

        // 1. Capture vehicles and pedestrians.
        for (MobileEntity entity : city.getMobileEntities()) {

            if (entity instanceof Vehicle vehicle) {

                Road currentRoad = vehicle.getCurrentRoad();

                String roadId = currentRoad == null
                        ? null
                        : currentRoad.getId();

                VehicleFamily family = familyOf(vehicle);

                VehicleFacts facts = new VehicleFacts(
                        roadId,
                        vehicle.getSpeedMetersPerSecond(),
                        family
                );

                if (vehicles.putIfAbsent(
                        vehicle.getId(), facts) != null) {
                    throw new IllegalStateException(
                            "Duplicate vehicle ID: " + vehicle.getId());
                }

                if (vehicle instanceof EmergencyVehicle
                        && roadId != null) {

                    emergencyRoads.add(roadId);
                }

            } else if (entity instanceof Pedestrian pedestrian) {

                String intersectionId = null;

                if (pedestrian.getCrosswalk() != null
                        && pedestrian.getCrosswalk()
                                .getIntersection() != null) {

                    intersectionId = pedestrian.getCrosswalk()
                            .getIntersection().getId();
                }

                PedestrianFacts facts = new PedestrianFacts(
                        pedestrian.getWaitingSeconds(),
                        intersectionId
                );

                if (pedestrians.putIfAbsent(
                        pedestrian.getId(), facts) != null) {
                    throw new IllegalStateException(
                            "Duplicate pedestrian ID: "
                            + pedestrian.getId());
                }
            }
        }

        // 2. Capture intersection and traffic signal information.
        for (Intersection intersection : city.getIntersections()) {

            IntersectionController controller =
                    intersection.getController();

            if (controller == null) {
                continue;
            }

            for (Road incoming : controller.getIncomingRoads()) {

                incomingRoadIntersections.put(
                        incoming.getId(),
                        intersection.getId()
                );

                // Emergency vehicles on incoming roads.
                if (emergencyRoads.contains(incoming.getId())) {
                    emergencyIntersections.add(
                            intersection.getId());
                }

                // Read the traffic signal state.
                TrafficSignal signal = controller.getSignalFor(incoming);

                if (signal != null) {
                    signalStates.put(
                            incoming.getId(),
                            signal.getState()
                    );
                }
            }
        }

        return new RuleObservations(
                vehicles,
                pedestrians,
                incomingRoadIntersections,
                signalStates,
                emergencyRoads,
                emergencyIntersections
        );
    }

    // Determine the supported family using Java subtyping.
    private static VehicleFamily familyOf(Vehicle vehicle) {

        if (vehicle instanceof EmergencyVehicle) {
            return VehicleFamily.EMERGENCY;
        }

        if (vehicle instanceof Car) {
            return VehicleFamily.CAR;
        }

        if (vehicle instanceof Bus) {
            return VehicleFamily.BUS;
        }

        if (vehicle instanceof Truck) {
            return VehicleFamily.TRUCK;
        }

        return VehicleFamily.OTHER;
    }

    public VehicleFacts vehicle(String id) {

        VehicleFacts facts = vehicles.get(
                Objects.requireNonNull(id));

        if (facts == null) {
            throw new IllegalArgumentException(
                    "Vehicle not in snapshot: " + id);
        }

        return facts;
    }

    public PedestrianFacts pedestrian(String id) {

        PedestrianFacts facts = pedestrians.get(
                Objects.requireNonNull(id));

        if (facts == null) {
            throw new IllegalArgumentException(
                    "Pedestrian not in snapshot: " + id);
        }

        return facts;
    }

    public boolean emergencyOnRoad(String roadId) {
        return roadId != null
                && emergencyRoads.contains(roadId);
    }

    public boolean emergencyAtIntersection(String intersectionId) {
        return intersectionId != null
                && emergencyIntersections.contains(intersectionId);
    }

    public String intersectionForIncomingRoad(String roadId) {
        return intersectionByIncomingRoad.get(roadId);
    }

    public SignalState signalStateForRoad(String roadId) {
        return signalStatesByIncomingRoad.get(roadId);
    }
}
