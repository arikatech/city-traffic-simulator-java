package com.smartcity.simulator.city;

import java.util.Objects;

public final class BusStop {

    private final String id;
    private final Lane lane;
    private final double positionMeters;

    public BusStop(String id, Lane lane, double positionMeters) {
        this.id = Objects.requireNonNull(id, "Stop ID cannot be null.");
        this.lane = Objects.requireNonNull(lane, "Stop lane cannot be null.");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Stop ID cannot be blank.");
        }

        if (!Double.isFinite(positionMeters)
                || positionMeters <= 0.0
                || positionMeters >= lane.getRoad().getLengthMeters()) {

            throw new IllegalArgumentException(
                    "Stop position must be inside the road."
            );
        }

        this.positionMeters = positionMeters;
    }

    public String getId() {
        return id;
    }

    public Lane getLane() {
        return lane;
    }

    public double getPositionMeters() {
        return positionMeters;
    }
}