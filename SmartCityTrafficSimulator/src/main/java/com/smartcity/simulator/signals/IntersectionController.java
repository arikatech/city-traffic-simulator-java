package com.smartcity.simulator.signals;

import com.smartcity.simulator.city.Crosswalk;
import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.pedestrians.PedestrianState;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IntersectionController {

    private static final double CONFLICT_ZONE_METERS = 5.0;
    private static final double PRIORITY_REQUEST_DISTANCE_METERS = 20.0;

    private final List<Road> incomingRoads;
    private final List<TrafficSignal> signals;

    private final double greenSeconds;
    private final double yellowSeconds;
    private final double clearanceSeconds;

    private int activeRoadIndex = 0;
    private SignalState phase = SignalState.RED;
    private double elapsedInPhaseSeconds = 0.0;

    private String reservedVehicleId;

    private String reservedPedestrianId;
    private Crosswalk reservedCrosswalk;

    private boolean crossingRequested;
    private boolean pedestrianPhase;
    private boolean pedestrianAdmissionOpen;
    private boolean vehicleTurnRequired;

    private int requestedRoadIndex = -1;
    private int nextRegularRoadIndex;
    private boolean priorityTurnUsed;
    // Stage 5: Priority requests submitted by the rule system.
    private enum RuleRequestKind {
        VEHICLE, PEDESTRIAN, SIGNAL
    }

    private record RulePriorityRequest(
            RuleRequestKind kind,
            String subjectId,
            SignalState phase) {
    }

    private final List<RulePriorityRequest> rulePriorityRequests =
            new ArrayList<>();

    private Set<String> waitingPedestrianIds = Set.of();

    private boolean selectedPedestrianRulePriority;
    private boolean selectedRoadRulePriority;

    private static final double PRIORITY_MIN_GREEN_SECONDS = 2.0;

    public IntersectionController(
            List<Road> incomingRoads,
            double greenSeconds,
            double yellowSeconds,
            double clearanceSeconds) {

        Objects.requireNonNull(
                incomingRoads,
                "Incoming roads cannot be null."
        );

        if (incomingRoads.isEmpty()) {
            throw new IllegalArgumentException(
                    "An intersection needs at least one incoming road."
            );
        }

        List<Road> orderedRoads = new ArrayList<>(incomingRoads);
        Set<String> roadIds = new HashSet<>();

        for (Road road : orderedRoads) {
            Objects.requireNonNull(
                    road,
                    "Incoming road cannot be null."
            );

            if (!roadIds.add(road.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate incoming road ID: " + road.getId()
                );
            }
        }

        orderedRoads.sort(Comparator.comparing(Road::getId));
        this.incomingRoads = List.copyOf(orderedRoads);

        this.greenSeconds = requirePositiveFinite(
                greenSeconds,
                "Green duration"
        );

        this.yellowSeconds = requirePositiveFinite(
                yellowSeconds,
                "Yellow duration"
        );

        this.clearanceSeconds = requirePositiveFinite(
                clearanceSeconds,
                "Clearance duration"
        );

        List<TrafficSignal> createdSignals = new ArrayList<>();

        for (Road road : this.incomingRoads) {
            createdSignals.add(new TrafficLight(road, this));
        }

        this.signals = List.copyOf(createdSignals);
    }

    public List<Road> getIncomingRoads() {
        return incomingRoads;
    }

    public List<TrafficSignal> getSignals() {
        return signals;
    }

    public TrafficSignal getSignalFor(Road incomingRoad) {
        int index = findRoadIndex(incomingRoad);
        return signals.get(index);
    }
    
    
    // ========================================
    // STAGE 5: RULE PRIORITY REQUESTS
    // ========================================

    public void clearRulePriorityRequests() {
        rulePriorityRequests.clear();
    }

    public void requestRuleVehiclePriority(String vehicleId) {

        requireNonBlankId(vehicleId);

        rulePriorityRequests.add(
                new RulePriorityRequest(
                        RuleRequestKind.VEHICLE,
                        vehicleId,
                        null));
    }

    public void requestRulePedestrianPriority(
            String pedestrianId) {

        requireNonBlankId(pedestrianId);

        rulePriorityRequests.add(
                new RulePriorityRequest(
                        RuleRequestKind.PEDESTRIAN,
                        pedestrianId,
                        null));
    }

    public void requestRuleSignalPhase(
            String incomingRoadId,
            SignalState desiredPhase) {

        requireNonBlankId(incomingRoadId);

        if (desiredPhase != SignalState.GREEN
                && desiredPhase != SignalState.RED) {
            throw new IllegalArgumentException(
                    "Only GREEN or RED may be requested");
        }

        boolean knownRoad = incomingRoads.stream()
                .anyMatch(road ->
                        road.getId().equals(incomingRoadId));

        if (!knownRoad) {
            throw new IllegalArgumentException(
                    "Unknown incoming road: " + incomingRoadId);
        }

        rulePriorityRequests.add(
                new RulePriorityRequest(
                        RuleRequestKind.SIGNAL,
                        incomingRoadId,
                        desiredPhase));
    }

    private static void requireNonBlankId(String id) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "A non-empty identifier is required");
        }
    }


    /** Read-only current signal state for the UI and inspection. */
    public SignalState getStateFor(Road incomingRoad) {
        int index = findRoadIndex(incomingRoad);

        if (index == activeRoadIndex) {
            return phase;
        }

        return SignalState.RED;
    }

    public void advance(double deltaSeconds) {
        requirePositiveFinite(deltaSeconds, "Time step");

        // Hold all vehicle signals red during the pedestrian phase.
        if (pedestrianPhase) {
            if (reservedPedestrianId == null
                    && (!pedestrianAdmissionOpen || !crossingRequested)) {

                pedestrianPhase = false;
                pedestrianAdmissionOpen = false;
                vehicleTurnRequired = true;

                // Begin a new clearance period after pedestrians.
                elapsedInPhaseSeconds = 0.0;
            }

            return;
        }

        double requiredSeconds = switch (phase) {
            case RED -> clearanceSeconds;
            case GREEN -> greenDurationWithRulePriority();
            case YELLOW -> yellowSeconds;
        };

        elapsedInPhaseSeconds = Math.min(
                requiredSeconds,
                elapsedInPhaseSeconds + deltaSeconds
        );

        if (elapsedInPhaseSeconds < requiredSeconds) {
            return;
        }

        // Clearance cannot end while the conflict zone is occupied.
        if (phase == SignalState.RED && isReserved()) {
            return;
        }

        if (phase == SignalState.RED
                && crossingRequested
                && !vehicleTurnRequired) {

            pedestrianPhase = true;
            pedestrianAdmissionOpen = true;
            elapsedInPhaseSeconds = 0.0;

            return;
        }

        switch (phase) {
            case RED -> {
                selectNextVehicleApproach();
                phase = SignalState.GREEN;
            }

            case GREEN -> {
                phase = SignalState.YELLOW;
                vehicleTurnRequired = false;
            }

            case YELLOW -> phase = SignalState.RED;
        }

        elapsedInPhaseSeconds = 0.0;
    }

    public boolean isReserved() {
        return reservedVehicleId != null
                || reservedPedestrianId != null;
    }

    public String getReservedVehicleId() {
        return reservedVehicleId;
    }

    public double getRequiredExitSpaceMeters(double vehicleLength) {
        return CONFLICT_ZONE_METERS
                + requirePositiveFinite(vehicleLength, "Vehicle length");
    }

    public boolean tryReserve(
            String vehicleId,
            Road incomingRoad,
            Lane exitLane,
            double vehicleLength) {

        Objects.requireNonNull(vehicleId);
        Objects.requireNonNull(exitLane);

        if (vehicleId.isBlank()) {
            throw new IllegalArgumentException(
                    "Vehicle ID cannot be blank."
            );
        }

        requirePositiveFinite(vehicleLength, "Vehicle length");

        TrafficSignal signal = getSignalFor(incomingRoad);
        Road exitRoad = exitLane.getRoad();

        if (!incomingRoad.getOutgoingRoads().contains(exitRoad)
                || !exitRoad.getLanes().contains(exitLane)) {

            throw new IllegalArgumentException(
                    "Reservation must follow a road connection."
            );
        }

        double releaseAt = getRequiredExitSpaceMeters(vehicleLength);

        if (!signal.canProceed()
                || isReserved()
                || !exitRoad.isEntryOpen()
                || exitRoad.getLengthMeters() < releaseAt) {

            return false;
        }

        reservedVehicleId = vehicleId;
        return true;
    }

    public void refreshReservation(List<? extends Vehicle> vehicles) {
        String occupant = null;

        for (Vehicle vehicle : vehicles) {
            if (isExitRoad(vehicle.getCurrentRoad())
                    && vehicle.getPositionMeters()
                            - vehicle.getLengthMeters()
                            < CONFLICT_ZONE_METERS) {

                if (occupant != null) {
                    throw new IllegalArgumentException(
                            "Multiple vehicles already occupy one conflict zone."
                    );
                }

                occupant = vehicle.getId();
            }
        }

        reservedVehicleId = occupant;
    }

    public boolean canStartPedestrianCrossing() {
        return pedestrianPhase
                && pedestrianAdmissionOpen
                && !isReserved();
    }

    public boolean tryReserveCrossing(
            String pedestrianId,
            Crosswalk crosswalk) {

        Objects.requireNonNull(pedestrianId);
        Objects.requireNonNull(crosswalk);

        if (pedestrianId.isBlank()
                || crosswalk.getController() != this) {

            throw new IllegalArgumentException(
                    "Invalid crossing request."
            );
        }

        if (!canStartPedestrianCrossing()) {
            return false;
        }

        reservedPedestrianId = pedestrianId;
        reservedCrosswalk = crosswalk;
        pedestrianAdmissionOpen = false;

        return true;
    }

    public boolean isCrossingReservedFor(
            String pedestrianId,
            Crosswalk crosswalk) {

        return pedestrianId != null
                && pedestrianId.equals(reservedPedestrianId)
                && reservedCrosswalk == crosswalk;
    }

    public void refreshCrossings(
            List<? extends Pedestrian> pedestrians) {

        boolean requested = false;
        String occupant = null;
        Crosswalk crossing = null;

        Set<String> waitingIds = new HashSet<>();

        for (Pedestrian pedestrian : pedestrians) {
            if (pedestrian.getCrosswalk().getController() != this) {
                continue;
            }

            if (pedestrian.getState() == PedestrianState.WAITING) {
                requested = true;
                waitingIds.add(pedestrian.getId());

            } else if (pedestrian.getState()
                    == PedestrianState.CROSSING) {

                if (occupant != null
                        || reservedVehicleId != null
                        || !pedestrianPhase) {

                    throw new IllegalArgumentException(
                            "Conflicting initial crossing occupancy."
                    );
                }

                occupant = pedestrian.getId();
                crossing = pedestrian.getCrosswalk();
            }
        }

        crossingRequested = requested;
        waitingPedestrianIds = Set.copyOf(waitingIds);
        reservedPedestrianId = occupant;
        reservedCrosswalk = crossing;
    }

    
    public void refreshPriorityRequests(
            List<? extends Vehicle> vehicles) {

        requestedRoadIndex = -1;
        selectedPedestrianRulePriority = false;
        selectedRoadRulePriority = false;

        String selectedId = null;

        // 1. Preserve the existing built-in
        // emergency-vehicle priority behavior.
        for (Vehicle vehicle : vehicles) {

            if (!vehicle.requestsSignalPriority()
                    || !vehicle.hasNextRoad()) {
                continue;
            }

            int index = incomingRoads.indexOf(
                    vehicle.getCurrentRoad());

            if (index < 0
                    || vehicle.getCurrentRoad().getLengthMeters()
                            - vehicle.getPositionMeters()
                            > PRIORITY_REQUEST_DISTANCE_METERS) {
                continue;
            }

            if (selectedId == null
                    || vehicle.getId().compareTo(selectedId) < 0) {

                selectedId = vehicle.getId();
                requestedRoadIndex = index;
            }
        }

        // Preserve existing automatic emergency precedence.
        if (selectedId != null) {
            return;
        }

        // 2. Consider user-rule requests in their
        // already resolved priority order.
        for (RulePriorityRequest request : rulePriorityRequests) {

            switch (request.kind()) {

                case VEHICLE -> {

                    for (Vehicle vehicle : vehicles) {

                        if (!vehicle.getId()
                                .equals(request.subjectId())
                                || !vehicle.hasNextRoad()) {
                            continue;
                        }

                        int index = incomingRoads.indexOf(
                                vehicle.getCurrentRoad());

                        if (index < 0) {
                            continue;
                        }

                        double distance =
                                vehicle.getCurrentRoad()
                                        .getLengthMeters()
                                - vehicle.getPositionMeters();

                        if (distance < 0
                                || distance
                                        > PRIORITY_REQUEST_DISTANCE_METERS) {
                            continue;
                        }

                        requestedRoadIndex = index;
                        selectedRoadRulePriority = true;
                        return;
                    }
                }

                case PEDESTRIAN -> {

                    if (!vehicleTurnRequired
                            && waitingPedestrianIds.contains(
                                    request.subjectId())) {

                        selectedPedestrianRulePriority = true;
                        return;
                    }
                }

                case SIGNAL -> {

                    int requestedIndex = -1;

                    for (int i = 0; i < incomingRoads.size(); i++) {

                        if (incomingRoads.get(i).getId()
                                .equals(request.subjectId())) {

                            requestedIndex = i;
                            break;
                        }
                    }

                    if (requestedIndex < 0) {
                        continue;
                    }

                    if (request.phase() == SignalState.RED) {

                        // RED means avoid selecting this road
                        // at the next safe vehicle phase.
                        // Do not directly switch a live signal.
                        if (incomingRoads.size() < 2) {
                            continue;
                        }

                        int alternative = -1;

                        for (int offset = 0;
                                offset < incomingRoads.size();
                                offset++) {

                            int candidate =
                                    (nextRegularRoadIndex + offset)
                                    % incomingRoads.size();

                            if (candidate != requestedIndex) {
                                alternative = candidate;
                                break;
                            }
                        }

                        if (alternative < 0) {
                            continue;
                        }

                        requestedRoadIndex = alternative;

                    } else {

                        // GREEN means favor this road
                        // at its next safe opportunity.
                        requestedRoadIndex = requestedIndex;
                    }

                    selectedRoadRulePriority = true;
                    return;
                }
            }
        }
    }

    public String getRequestedPriorityRoadId() {
        return requestedRoadIndex < 0
                ? null
                : incomingRoads.get(requestedRoadIndex).getId();
    }
    
    private double greenDurationWithRulePriority() {

        // Allow an eligible waiting pedestrian priority
        // to bring the green phase to a safe early end.
        // YELLOW and RED clearance are still mandatory.
        if (selectedPedestrianRulePriority
                && !vehicleTurnRequired) {

            return Math.min(
                    greenSeconds,
                    PRIORITY_MIN_GREEN_SECONDS);
        }

        // If a rule favors another road, the current
        // green can finish early, but never skip yellow.
        if (selectedRoadRulePriority
                && requestedRoadIndex >= 0
                && requestedRoadIndex != activeRoadIndex
                && !priorityTurnUsed) {

            return Math.min(
                    greenSeconds,
                    PRIORITY_MIN_GREEN_SECONDS);
        }

        return greenSeconds;
    }

    private void selectNextVehicleApproach() {
        if (requestedRoadIndex >= 0 && !priorityTurnUsed) {
            activeRoadIndex = requestedRoadIndex;
            priorityTurnUsed = true;

        } else {
            activeRoadIndex = nextRegularRoadIndex;

            nextRegularRoadIndex =
                    (nextRegularRoadIndex + 1) % incomingRoads.size();

            priorityTurnUsed = false;
        }
    }

    private int findRoadIndex(Road road) {
        Objects.requireNonNull(
                road,
                "Incoming road cannot be null."
        );

        for (int i = 0; i < incomingRoads.size(); i++) {
            if (incomingRoads.get(i) == road) {
                return i;
            }
        }

        throw new IllegalArgumentException(
                "Road is not an approach to this intersection."
        );
    }

    private boolean isExitRoad(Road road) {
        for (Road incoming : incomingRoads) {
            if (incoming.getOutgoingRoads().contains(road)) {
                return true;
            }
        }

        return false;
    }

    private static double requirePositiveFinite(
            double value,
            String name) {

        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be positive and finite."
            );
        }

        return value;
    }
}