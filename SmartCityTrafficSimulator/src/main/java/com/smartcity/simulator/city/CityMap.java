package com.smartcity.simulator.city;

import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.vehicles.Vehicle;
import com.smartcity.simulator.pedestrians.Pedestrian;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CityMap {

    private final List<Road> roads = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();
    private final List<MobileEntity> mobileEntities = new ArrayList<>();

    public void addRoad(Road road) {
        Objects.requireNonNull(road, "Road cannot be null.");

        for (Road existing : roads) {
            if (existing.getId().equals(road.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate road ID: " + road.getId()
                );
            }
        }

        roads.add(road);
    }

    public void addIntersection(Intersection intersection) {
        Objects.requireNonNull(
                intersection,
                "Intersection cannot be null."
        );

        for (Road incoming : intersection.getIncomingRoads()) {
            if (!roads.contains(incoming)) {
                throw new IllegalArgumentException(
                        "Every incoming road must belong to this city map."
                );
            }
        }

        for (Intersection existing : intersections) {
            if (existing.getId().equals(intersection.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate intersection ID: " + intersection.getId()
                );
            }

            for (Road incoming : intersection.getIncomingRoads()) {
                if (existing.getIncomingRoads().contains(incoming)) {
                    throw new IllegalArgumentException(
                            "An incoming road cannot end at two intersections."
                    );
                }
            }
        }

        intersections.add(intersection);
    }

    public List<Intersection> getIntersections() {
        return List.copyOf(intersections);
    }

    public void addVehicle(Vehicle vehicle) {
        Objects.requireNonNull(
                vehicle,
                "Vehicle cannot be null."
        );

        Road road = vehicle.getCurrentLane().getRoad();

        if (!roads.contains(road)
                || !road.getLanes().contains(vehicle.getCurrentLane())) {

            throw new IllegalArgumentException(
                    "Vehicle's lane is not part of this city."
            );
        }

        for (MobileEntity existing : mobileEntities) {
            if (existing.getId().equals(vehicle.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate entity ID: " + vehicle.getId()
                );
            }
        }

        mobileEntities.add(vehicle);
    }

    public void connectRoads(Road from, Road to) {
        Objects.requireNonNull(
                from,
                "Starting road cannot be null"
        );

        Objects.requireNonNull(
                to,
                "Destination road cannot be null"
        );

        if (!roads.contains(from) || !roads.contains(to)) {
            throw new IllegalArgumentException(
                    "Both roads must belong to this city map"
            );
        }

        from.addOutgoingRoad(to);
    }
    
    public void addPedestrian(Pedestrian pedestrian) {
        Objects.requireNonNull(pedestrian, "Pedestrian cannot be null.");

        if (!intersections.contains(
                pedestrian.getCrosswalk().getIntersection())) {

            throw new IllegalArgumentException(
                    "Crosswalk must belong to this city."
            );
        }

        for (MobileEntity existing : mobileEntities) {
            if (existing.getId().equals(pedestrian.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate entity ID: " + pedestrian.getId()
                );
            }
        }

        mobileEntities.add(pedestrian);
    }

    /**
     * Remove a specific vehicle instance when its trip has finished.
     *
     * The simulation engine determines when a journey is complete;
     * CityMap only manages membership. A matching ID on a different
     * object does not authorize its removal.
     *
     * @return true if the vehicle belonged to this city and was removed
     */
    public boolean removeVehicle(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null.");
        for (int i = 0; i < mobileEntities.size(); i++) {
            if (mobileEntities.get(i) == vehicle) {
                mobileEntities.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Remove only the exact pedestrian instance after its committed arrival. */
    public boolean removePedestrian(Pedestrian pedestrian) {
        Objects.requireNonNull(pedestrian, "Pedestrian cannot be null.");
        for (int i = 0; i < mobileEntities.size(); i++) {
            if (mobileEntities.get(i) == pedestrian) {
                mobileEntities.remove(i);
                return true;
            }
        }
        return false;
    }

    public List<Road> getRoads() {
        return List.copyOf(roads);
    }

    public List<MobileEntity> getMobileEntities() {
        return List.copyOf(mobileEntities);
    }
}