package com.smartcity.simulator.statistics;

import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.pedestrians.PedestrianState;
import com.smartcity.simulator.simulation.CompletedTrip;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Mutable accumulator owned by ONE SimulationEngine. No render/UI calls.
 *
 * The engine calls captureBeforeProposals() before planning or committing, then
 * recordCommittedTick() after all commits and completed-vehicle removal.
 * Thus every wait / crossing observation comes from an actual logical tick.
 *
 * Queue definition for this bounded model: vehicles still present that made
 * no forward progress during the most recently completed logical tick.
 * This is a reproducible stopped-vehicle proxy, not a physical queue detector.
 */
public final class StatisticsCollector {
    private static final double STILL_METERS = 1e-8;

    private int completedTrips;
    private int completedEmergencyTrips;
    private double totalTripSeconds;
    private double totalEmergencyTripSeconds;
    private double vehicleWaitingSeconds;
    private double pedestrianWaitingSeconds;
    private int currentQueueLength;
    private int successfulPedestrianCrossings;

    // Identity, not ID: a new pedestrian with a reused ID is another individual.
    private final Set<Pedestrian> countedPedestrians =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private record Before(Lane lane, double positionMeters, PedestrianState state) { }

    /** Opaque capture of entity state at the beginning of a logical tick. */
    public static final class TickStart {
        private final Map<MobileEntity, Before> observations;
        private TickStart(Map<MobileEntity, Before> observations) {
            this.observations = observations;
        }
    }

    public TickStart captureBeforeProposals(List<? extends MobileEntity> participants) {
        Objects.requireNonNull(participants, "participants");
        Map<MobileEntity, Before> observations = new IdentityHashMap<>();
        for (MobileEntity entity : participants) {
            Objects.requireNonNull(entity, "participant");
            if (entity instanceof Vehicle vehicle) {
                observations.put(entity, new Before(
                        vehicle.getCurrentLane(), vehicle.getPositionMeters(), null));
            } else if (entity instanceof Pedestrian pedestrian) {
                observations.put(entity, new Before(null,
                        pedestrian.getPositionMeters(), pedestrian.getState()));
            }
        }
        return new TickStart(observations);
    }

    /** Record exactly one successfully committed logical tick. */
    public void recordCommittedTick(TickStart tickStart,
            List<? extends MobileEntity> entitiesStillInCity, double dt) {
        Objects.requireNonNull(tickStart, "tickStart");
        Objects.requireNonNull(entitiesStillInCity, "entitiesStillInCity");
        if (!Double.isFinite(dt) || dt <= 0) {
            throw new IllegalArgumentException("dt must be positive and finite");
        }

        Set<MobileEntity> present = Collections.newSetFromMap(new IdentityHashMap<>());
        present.addAll(entitiesStillInCity);
        int queue = 0;
        for (var entry : tickStart.observations.entrySet()) {
            MobileEntity entity = entry.getKey();
            Before before = entry.getValue();
            // A crossing is a committed result even if a future scenario
            // removes an ARRIVED pedestrian immediately afterward.
            if (entity instanceof Pedestrian pedestrian
                    && before.state() != PedestrianState.ARRIVED
                    && pedestrian.getState() == PedestrianState.ARRIVED
                    && countedPedestrians.add(pedestrian)) {
                successfulPedestrianCrossings++;
            }
            if (!present.contains(entity)) continue; // completed/removed, not queued
            if (entity instanceof Vehicle vehicle) {
                boolean stopped = before.lane() == vehicle.getCurrentLane()
                        && Math.abs(before.positionMeters() - vehicle.getPositionMeters())
                        <= STILL_METERS;
                if (stopped) {
                    vehicleWaitingSeconds += dt;
                    queue++;
                }
            } else if (entity instanceof Pedestrian pedestrian) {
                PedestrianState now = pedestrian.getState();
                if (before.state() == PedestrianState.WAITING
                        && now == PedestrianState.WAITING) {
                    pedestrianWaitingSeconds += dt;
                }
            }
        }
        currentQueueLength = queue;
    }

    /** Called only from the engine's successful post-commit arrival path. */
    public void recordCompletedTrip(CompletedTrip trip) {
        Objects.requireNonNull(trip, "trip");
        completedTrips++;
        totalTripSeconds += trip.travelTimeSeconds();
        if (trip.emergency()) {
            completedEmergencyTrips++;
            totalEmergencyTripSeconds += trip.travelTimeSeconds();
        }
    }

    public StatisticsSnapshot snapshot() {
        return new StatisticsSnapshot(
                completedTrips,
                completedTrips == 0 ? OptionalDouble.empty()
                        : OptionalDouble.of(totalTripSeconds / completedTrips),
                vehicleWaitingSeconds + pedestrianWaitingSeconds,
                vehicleWaitingSeconds,
                pedestrianWaitingSeconds,
                currentQueueLength,
                successfulPedestrianCrossings,
                completedEmergencyTrips,
                completedEmergencyTrips == 0 ? OptionalDouble.empty()
                        : OptionalDouble.of(totalEmergencyTripSeconds / completedEmergencyTrips));
    }
}
