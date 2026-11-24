package com.smartcity.simulator.city;

import com.smartcity.simulator.signals.IntersectionController;
import com.smartcity.simulator.signals.PedestrianSignal;
import com.smartcity.simulator.signals.TrafficSignal;
import java.util.Objects;

public final class Crosswalk {

    private final String id;
    private final double lengthMeters;
    private final Intersection intersection;
    private final TrafficSignal signal;

    public Crosswalk(
            String id,
            double lengthMeters,
            Intersection intersection) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Crosswalk ID cannot be blank."
            );
        }

        if (!Double.isFinite(lengthMeters) || lengthMeters <= 0.0) {
            throw new IllegalArgumentException(
                    "Crosswalk length must be positive and finite."
            );
        }

        this.id = id;
        this.lengthMeters = lengthMeters;
        this.intersection = Objects.requireNonNull(intersection);
        this.signal = new PedestrianSignal(getController());
    }

    public String getId() {
        return id;
    }

    public double getLengthMeters() {
        return lengthMeters;
    }

    public Intersection getIntersection() {
        return intersection;
    }

    public IntersectionController getController() {
        return intersection.getController();
    }

    public TrafficSignal getSignal() {
        return signal;
    }

    public boolean tryReserve(String pedestrianId) {
        return getController().tryReserveCrossing(pedestrianId, this);
    }

    public boolean isReservedFor(String pedestrianId) {
        return getController().isCrossingReservedFor(pedestrianId, this);
    }
}