package com.smartcity.simulator.statistics;

import java.util.Objects;
import java.util.OptionalDouble;

/** A read-only collection of committed simulation metrics. No Swing dependencies. */
public record StatisticsSnapshot(
        int completedTrips,
        OptionalDouble meanTravelTimeSeconds,
        double cumulativeWaitingSeconds,
        double vehicleWaitingSeconds,
        double pedestrianWaitingSeconds,
        int currentQueueLength,
        int successfulPedestrianCrossings,
        int completedEmergencyTrips,
        OptionalDouble meanEmergencyTripTimeSeconds) {

    public StatisticsSnapshot {
        if (completedTrips < 0 || currentQueueLength < 0
                || successfulPedestrianCrossings < 0 || completedEmergencyTrips < 0
                || completedEmergencyTrips > completedTrips) {
            throw new IllegalArgumentException("Statistics counts must be nonnegative and consistent");
        }
        meanTravelTimeSeconds = Objects.requireNonNull(meanTravelTimeSeconds);
        meanEmergencyTripTimeSeconds = Objects.requireNonNull(meanEmergencyTripTimeSeconds);
        requireNonNegativeFinite(cumulativeWaitingSeconds, "total waiting");
        requireNonNegativeFinite(vehicleWaitingSeconds, "vehicle waiting");
        requireNonNegativeFinite(pedestrianWaitingSeconds, "pedestrian waiting");
        if (Math.abs(cumulativeWaitingSeconds
                - (vehicleWaitingSeconds + pedestrianWaitingSeconds)) > 1e-5) {
            throw new IllegalArgumentException("Waiting components must add up");
        }
        if ((completedTrips == 0) != meanTravelTimeSeconds.isEmpty()
                || (completedEmergencyTrips == 0) != meanEmergencyTripTimeSeconds.isEmpty()) {
            throw new IllegalArgumentException("Mean exists exactly when samples exist");
        }
        meanTravelTimeSeconds.ifPresent(v -> requireNonNegativeFinite(v, "mean trip"));
        meanEmergencyTripTimeSeconds.ifPresent(v -> requireNonNegativeFinite(v, "emergency mean"));
    }

    private static void requireNonNegativeFinite(double value, String description) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Invalid " + description);
        }
    }
}
