package com.smartcity.simulator.simulation;

import java.util.Objects;

/**
 * A vehicle arrival observed after a successfully committed logical tick.
 * This is an immutable fact for the future StatisticsCollector, not a
 * render event or an instruction to move/remove the vehicle.
 *
 * Departure time is the beginning of the first engine tick in which the
 * vehicle was present. Arrival time is the end of the committing tick.
 */
public record CompletedTrip(
        String vehicleId,
        String vehicleType,
        double departureSeconds,
        double arrivalSeconds,
        boolean emergency) {

    public CompletedTrip {
        Objects.requireNonNull(vehicleId, "Vehicle ID cannot be null");
        Objects.requireNonNull(vehicleType, "Vehicle type cannot be null");
        if (vehicleId.isBlank() || vehicleType.isBlank()) {
            throw new IllegalArgumentException("Vehicle ID/type must not be blank");
        }
        if (!Double.isFinite(departureSeconds)
                || !Double.isFinite(arrivalSeconds)
                || departureSeconds < 0.0
                || arrivalSeconds < departureSeconds) {
            throw new IllegalArgumentException("Invalid trip times");
        }
    }

    public double travelTimeSeconds() {
        return arrivalSeconds - departureSeconds;
    }
}
