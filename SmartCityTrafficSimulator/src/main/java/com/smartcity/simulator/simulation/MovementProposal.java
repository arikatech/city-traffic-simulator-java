package com.smartcity.simulator.simulation;

import com.smartcity.simulator.city.Lane;
import java.util.Objects;

public record MovementProposal(
        String entityId,
        double fromMeters,
        double toMeters,
        Lane fromLane,
        Lane toLane
) {

    public MovementProposal {
        Objects.requireNonNull(entityId);

        if (entityId.isBlank()) {
            throw new IllegalArgumentException(
                    "Entity ID cannot be blank");
        }

        if (!Double.isFinite(fromMeters) || fromMeters < 0.0
                || !Double.isFinite(toMeters) || toMeters < 0.0) {
            throw new IllegalArgumentException(
                    "Positions must be nonnegative and finite");
        }

        if ((fromLane == null) != (toLane == null)) {
            throw new IllegalArgumentException(
                    "Supply both lanes or neither lane");
        }
    }

    // Keep older Stage 1 movement proposals compatible.
    public MovementProposal(
            String entityId,
            double fromMeters,
            double toMeters) {

        this(entityId, fromMeters, toMeters, null, null);
    }

    public boolean changesLane() {
        return fromLane != null && fromLane != toLane;
    }
}