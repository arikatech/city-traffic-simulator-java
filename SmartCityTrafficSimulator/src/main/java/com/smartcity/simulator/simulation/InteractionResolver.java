package com.smartcity.simulator.simulation;

import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.pedestrians.PedestrianState;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.*;

public class InteractionResolver {

    private final double minimumGapMeters;

    public InteractionResolver(double minimumGapMeters) {
        if (!Double.isFinite(minimumGapMeters)
                || minimumGapMeters <= 0.0) {

            throw new IllegalArgumentException(
                    "Minimum gap must be positive and finite"
            );
        }

        this.minimumGapMeters = minimumGapMeters;
    }

    public List<MovementProposal> resolve(
            List<? extends MobileEntity> entities,
            List<MovementProposal> proposals) {

        return resolve(entities, proposals, List.of());
    }

    public List<MovementProposal> resolve(
            List<? extends MobileEntity> entities,
            List<MovementProposal> proposals,
            List<Intersection> intersections) {

        Objects.requireNonNull(entities);
        Objects.requireNonNull(proposals);

        intersections = List.copyOf(intersections);

        if (entities.size() != proposals.size()) {
            throw new IllegalArgumentException(
                    "Every entity must have one movement proposal"
            );
        }

        List<Vehicle> vehicles = new ArrayList<>();
        List<Pedestrian> pedestrians = new ArrayList<>();
        Set<String> entityIds = new HashSet<>();

        for (MobileEntity entity : entities) {
            Objects.requireNonNull(entity);

            if (!entityIds.add(entity.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate entity ID"
                );
            }

            if (entity instanceof Vehicle vehicle) {
                vehicles.add(vehicle);

            } else if (entity instanceof Pedestrian pedestrian) {
                pedestrians.add(pedestrian);

            } else {
                throw new IllegalArgumentException(
                        "Unsupported mobile entity."
                );
            }
        }

        Map<String, MovementProposal> proposedById = new HashMap<>();

        for (MovementProposal proposal : proposals) {
            Objects.requireNonNull(proposal);

            if (!entityIds.contains(proposal.entityId())
                    || proposedById.putIfAbsent(
                            proposal.entityId(), proposal) != null) {

                throw new IllegalArgumentException(
                        "Unknown or duplicate movement proposal"
                );
            }
        }

        // Validate all proposals before making admission decisions.
        for (Vehicle vehicle : vehicles) {
            validateProposalShape(
                    vehicle,
                    proposedById.get(vehicle.getId())
            );
        }

        for (Pedestrian pedestrian : pedestrians) {
            MovementProposal proposal =
                    proposedById.get(pedestrian.getId());

            if (proposal == null
                    || proposal.fromLane() != null
                    || proposal.toLane() != null
                    || Double.compare(
                            proposal.fromMeters(),
                            pedestrian.getPositionMeters()) != 0
                    || proposal.toMeters() < proposal.fromMeters()
                    || proposal.toMeters()
                            > pedestrian.getCrosswalk().getLengthMeters()) {

                throw new IllegalArgumentException(
                        "Invalid pedestrian proposal."
                );
            }

            if (!intersections.contains(
                    pedestrian.getCrosswalk().getIntersection())) {

                throw new IllegalArgumentException(
                        "Crosswalk intersection is missing."
                );
            }
        }

        // Reject invalid starting vehicle arrangements.
        for (Vehicle vehicle : vehicles) {
            if (!isSafe(
                    getMovementSegments(
                            vehicle,
                            createWaitingProposal(vehicle)
                    ),
                    vehicle,
                    vehicles,
                    List.of())) {

                throw new IllegalArgumentException(
                        "Initial vehicle bodies violate the minimum gap."
                );
            }
        }

        // Observe committed starting positions and current requests.
        for (Intersection intersection : intersections) {
            intersection.getController().refreshReservation(vehicles);
            intersection.getController().refreshCrossings(pedestrians);
            intersection.getController().refreshPriorityRequests(vehicles);
        }

        // Emergency vehicles first, with stable ID ordering.
        vehicles.sort(
                Comparator.comparing(Vehicle::requestsSignalPriority)
                        .reversed()
                        .thenComparing(Vehicle::getId)
        );

        List<LaneSegment> reservations = new ArrayList<>();
        Map<String, MovementProposal> decisions = new HashMap<>();

        for (Vehicle vehicle : vehicles) {
            MovementProposal proposed =
                    proposedById.get(vehicle.getId());

            List<LaneSegment> path =
                    getMovementSegments(vehicle, proposed);

            boolean admitted = isSafe(
                    path,
                    vehicle,
                    vehicles,
                    reservations
            );

            // Stage 4: Check destination entry restrictions.
            boolean entryBlocked = false;

            if (proposed.changesLane()) {

                Lane destinationLane = proposed.toLane();

                entryBlocked =
                        !destinationLane.getRoad().isEntryOpen()
                        || !destinationLane.isEntryOpen();
            }

            if (entryBlocked) {
                admitted = false;
            }

            Intersection intersection = findIntersection(
                    vehicle.getCurrentRoad(),
                    intersections
            );

            if (admitted
                    && proposed.changesLane()
                    && intersection != null) {

                double exitSpace = intersection.getController()
                        .getRequiredExitSpaceMeters(
                                vehicle.getLengthMeters()
                        );

                LaneSegment receivingSpace = new LaneSegment(
                        proposed.toLane(),
                        0.0,
                        exitSpace
                );

                admitted = isSafe(
                        List.of(receivingSpace),
                        vehicle,
                        vehicles,
                        reservations
                ) && intersection.getController().tryReserve(
                        vehicle.getId(),
                        vehicle.getCurrentRoad(),
                        proposed.toLane(),
                        vehicle.getLengthMeters()
                );
            }

            MovementProposal decision = proposed;

            if (!admitted) {
                if (entryBlocked) {

                    // Closed destination: wait without entering.
                    decision = createWaitingProposal(vehicle);

                } else {
                    // Existing Stage 3 behavior for other denials.
                    decision = proposed.changesLane()
                            ? createStopLineProposal(vehicle)
                            : createWaitingProposal(vehicle);
                }

                if (!isSafe(
                        getMovementSegments(vehicle, decision),
                        vehicle,
                        vehicles,
                        reservations)) {

                    decision = createWaitingProposal(vehicle);
                }
            }

            decisions.put(vehicle.getId(), decision);

            reservations.addAll(
                    getMovementSegments(vehicle, decision)
            );
        }

        // Longest-waiting pedestrians first, then stable ID ordering.
        pedestrians.sort(
                Comparator.comparingDouble(Pedestrian::getWaitingSeconds)
                        .reversed()
                        .thenComparing(Pedestrian::getId)
        );

        for (Pedestrian pedestrian : pedestrians) {
            MovementProposal proposed =
                    proposedById.get(pedestrian.getId());

            MovementProposal decision = proposed;

            if (proposed.toMeters() > proposed.fromMeters()) {
                boolean admitted =
                        pedestrian.getState() == PedestrianState.WAITING
                        ? pedestrian.getCrosswalk().tryReserve(
                                pedestrian.getId())
                        : pedestrian.getCrosswalk().isReservedFor(
                                pedestrian.getId());

                if (!admitted) {
                    decision = new MovementProposal(
                            pedestrian.getId(),
                            pedestrian.getPositionMeters(),
                            pedestrian.getPositionMeters()
                    );
                }
            }

            decisions.put(pedestrian.getId(), decision);
        }

        // Return decisions in the original entity order.
        List<MovementProposal> results = new ArrayList<>();

        for (MobileEntity entity : entities) {
            results.add(decisions.get(entity.getId()));
        }

        return List.copyOf(results);
    }

    private List<LaneSegment> getMovementSegments(
            Vehicle vehicle,
            MovementProposal proposal) {

        Lane fromLane = vehicle.getCurrentLane();

        Lane toLane = proposal.toLane() == null
                ? fromLane
                : proposal.toLane();

        List<LaneSegment> path = new ArrayList<>();

        double rear = Math.max(
                0.0,
                proposal.fromMeters() - vehicle.getLengthMeters()
        );

        path.add(new LaneSegment(
                fromLane,
                rear,
                fromLane == toLane
                        ? proposal.toMeters()
                        : fromLane.getRoad().getLengthMeters()
        ));

        if (fromLane != toLane) {
            path.add(new LaneSegment(
                    toLane,
                    0.0,
                    proposal.toMeters()
            ));
        }

        // Protect any tail remaining on the previous lane.
        Lane previous = vehicle.getPreviousLane();

        if (previous != null
                && proposal.fromMeters() < vehicle.getLengthMeters()) {

            double tailStart = previous.getRoad().getLengthMeters()
                    - (vehicle.getLengthMeters()
                            - proposal.fromMeters());

            path.add(new LaneSegment(
                    previous,
                    tailStart,
                    previous.getRoad().getLengthMeters()
            ));
        }

        return path;
    }

    private void validateProposalShape(
            Vehicle vehicle,
            MovementProposal proposal) {

        if (proposal == null) {
            throw new IllegalArgumentException(
                    "Missing movement proposal"
            );
        }

        Lane currentLane = vehicle.getCurrentLane();

        if (proposal.fromLane() != null
                && proposal.fromLane() != currentLane) {

            throw new IllegalArgumentException(
                    "Proposal has an outdated starting lane"
            );
        }

        if (Double.compare(
                proposal.fromMeters(),
                vehicle.getPositionMeters()) != 0) {

            throw new IllegalArgumentException(
                    "Proposal has an outdated starting position"
            );
        }

        Lane destination = proposal.toLane() == null
                ? currentLane
                : proposal.toLane();

        if (proposal.fromMeters()
                > currentLane.getRoad().getLengthMeters()
                || proposal.toMeters()
                        > destination.getRoad().getLengthMeters()) {

            throw new IllegalArgumentException(
                    "Movement exceeds road boundaries"
            );
        }

        if (destination == currentLane) {
            if (proposal.toMeters() < proposal.fromMeters()) {
                throw new IllegalArgumentException(
                        "Vehicles cannot move backwards"
                );
            }

        } else if (!vehicle.hasNextRoad()
                || destination.getRoad() != vehicle.getNextRoad()
                || destination.getRoad().getLengthMeters()
                        < vehicle.getLengthMeters()
                || !vehicle.getNextRoad().getLanes().contains(destination)) {

            throw new IllegalArgumentException(
                    "Movement does not follow a valid route"
            );
        }   
    }

    private boolean isSafe(
            List<LaneSegment> path,
            Vehicle movingVehicle,
            List<Vehicle> vehicles,
            List<LaneSegment> reservations) {

        for (LaneSegment segment : path) {
            // Protect other vehicles' committed starting bodies.
            for (Vehicle other : vehicles) {
                if (other == movingVehicle) {
                    continue;
                }

                List<LaneSegment> occupied = getMovementSegments(
                        other,
                        createWaitingProposal(other)
                );

                for (LaneSegment body : occupied) {
                    if (body.lane() == segment.lane()
                            && distanceBetween(segment, body)
                                    < minimumGapMeters) {

                        return false;
                    }
                }
            }

            // Protect movements already accepted this tick.
            for (LaneSegment reserved : reservations) {
                if (reserved.lane() == segment.lane()
                        && distanceBetween(segment, reserved)
                                < minimumGapMeters) {

                    return false;
                }
            }
        }

        return true;
    }

    private double distanceBetween(
            LaneSegment first,
            LaneSegment second) {

        if (first.end() < second.start()) {
            return second.start() - first.end();
        }

        if (second.end() < first.start()) {
            return first.start() - second.end();
        }

        return 0.0;
    }

    private MovementProposal createWaitingProposal(Vehicle vehicle) {
        return new MovementProposal(
                vehicle.getId(),
                vehicle.getPositionMeters(),
                vehicle.getPositionMeters(),
                vehicle.getCurrentLane(),
                vehicle.getCurrentLane()
        );
    }

    private MovementProposal createStopLineProposal(Vehicle vehicle) {
        Lane lane = vehicle.getCurrentLane();

        return new MovementProposal(
                vehicle.getId(),
                vehicle.getPositionMeters(),
                lane.getRoad().getLengthMeters(),
                lane,
                lane
        );
    }

    private Intersection findIntersection(
            Road road,
            List<Intersection> intersections) {

        for (Intersection intersection : intersections) {
            if (intersection.getIncomingRoads().contains(road)) {
                return intersection;
            }
        }

        return null;
    }

    private record LaneSegment(
            Lane lane,
            double start,
            double end) {
    }
}