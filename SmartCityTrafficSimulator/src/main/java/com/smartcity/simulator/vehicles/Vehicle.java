package com.smartcity.simulator.vehicles;

import com.smartcity.simulator.behaviours.DrivingBehaviour;
import com.smartcity.simulator.behaviours.NormalDriving;
import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.simulation.MovementProposal;
import java.util.Objects;

public abstract class Vehicle extends MobileEntity {

    private Lane currentLane;
    private Lane previousLane;

    private double speedMetersPerSecond;
    private final double lengthMeters;

    private Route route;
    private int routeIndex = 0;

    private DrivingBehaviour drivingBehaviour = new NormalDriving();

    protected Vehicle(String id, Lane startingLane) {
        this(id, startingLane, 4.5);
    }

    protected Vehicle(
            String id,
            Lane startingLane,
            double lengthMeters) {

        super(id);

        this.currentLane = Objects.requireNonNull(
                startingLane,
                "Starting lane cannot be null."
        );

        if (!Double.isFinite(lengthMeters)
                || lengthMeters <= 0.0
                || lengthMeters
                        > startingLane.getRoad().getLengthMeters()) {

            throw new IllegalArgumentException(
                    "Vehicle length must be positive, finite and fit its road."
            );
        }

        this.lengthMeters = lengthMeters;
        this.speedMetersPerSecond = 0.0;
    }

    public Lane getCurrentLane() {
        return currentLane;
    }

    public Lane getPreviousLane() {
        return previousLane;
    }

    public double getLengthMeters() {
        return lengthMeters;
    }

    public double getSpeedMetersPerSecond() {
        return speedMetersPerSecond;
    }

    public abstract double getPhysicalMaximumSpeed();

    public void setSpeed(double candidate) {
        if (!Double.isFinite(candidate)
                || candidate < 0.0
                || candidate > getPhysicalMaximumSpeed()) {

            throw new IllegalArgumentException(
                    "Invalid vehicle speed: " + candidate
            );
        }

        speedMetersPerSecond = candidate;
    }

    public void setDrivingBehaviour(DrivingBehaviour behaviour) {
        drivingBehaviour = Objects.requireNonNull(behaviour);
    }
    
    public boolean requestsSignalPriority() {
        return false;
    }
    

    private double chooseLegalSpeed(Road road) {
        double cap = Math.min(
                getPhysicalMaximumSpeed(),
                road.getBaseSpeedLimit()
        );

        double chosen = drivingBehaviour.chooseSpeed(
                speedMetersPerSecond,
                cap
        );

        if (!Double.isFinite(chosen) || chosen < 0.0) {
            throw new IllegalArgumentException(
                    "Driving behaviour returned an invalid speed."
            );
        }

        return Math.min(
                chosen,
                Math.min(speedMetersPerSecond, cap)
        );
    }

    public void assignRoute(Route newRoute) {
        Objects.requireNonNull(newRoute, "Route cannot be null");

        if (newRoute.getStartRoad() != getCurrentRoad()) {
            throw new IllegalArgumentException(
                    "Route must start from the vehicle's current road"
            );
        }

        route = newRoute;
        routeIndex = 0;
    }

    @Override
    public MovementProposal planStep(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds)
                || deltaSeconds <= 0.0) {

            throw new IllegalArgumentException(
                    "Time step must be positive and finite."
            );
        }

        Road currentRoad = getCurrentRoad();
        double from = getPositionMeters();
        double legalSpeed = chooseLegalSpeed(currentRoad);

        double remainingDistance = Math.max(
                0.0,
                currentRoad.getLengthMeters() - from
        );

        double movement = legalSpeed * deltaSeconds;

        // Stay on the current road.
        if (movement <= remainingDistance || legalSpeed == 0.0) {
            return new MovementProposal(
                    getId(),
                    from,
                    Math.min(
                            currentRoad.getLengthMeters(),
                            from + movement
                    ),
                    currentLane,
                    currentLane
            );
        }

        // Reach the endpoint without entering another road.
        if (!hasNextRoad()
                || !getNextRoad().isEntryOpen()
                || getNextRoad().getLanes().isEmpty()
                || getNextRoad().getLengthMeters() < lengthMeters) {

            return new MovementProposal(
                    getId(),
                    from,
                    currentRoad.getLengthMeters(),
                    currentLane,
                    currentLane
            );
        }

        // Propose entering the next road.
        Road nextRoad = getNextRoad();
        Lane nextLane = nextRoad.getLanes().get(0);

        double timeToEnd = remainingDistance / legalSpeed;

        double remainingSeconds = Math.max(
                0.0,
                deltaSeconds - timeToEnd
        );

        double nextPosition = Math.min(
                nextRoad.getLengthMeters(),
                chooseLegalSpeed(nextRoad) * remainingSeconds
        );

        return new MovementProposal(
                getId(),
                from,
                nextPosition,
                currentLane,
                nextLane
        );
    }

    @Override
    protected void validateProposal(MovementProposal proposal) {
        if (proposal.fromLane() != null
                && proposal.fromLane() != currentLane) {

            throw new IllegalArgumentException(
                    "Proposal starts on an outdated lane."
            );
        }

        Lane destination = proposal.toLane() == null
                ? currentLane
                : proposal.toLane();

        if (destination == currentLane) {
            if (proposal.toMeters() < proposal.fromMeters()) {
                throw new IllegalArgumentException(
                        "Vehicle cannot move backwards."
                );
            }

            super.validateProposal(proposal);
            return;
        }

        if (!hasNextRoad()
                || destination.getRoad() != getNextRoad()
                || !destination.getRoad().isEntryOpen()
                || destination.getRoad().getLengthMeters() < lengthMeters
                || !getNextRoad().getLanes().contains(destination)) {

            throw new IllegalArgumentException(
                    "Movement does not follow an open route"
            );
        }

        if (proposal.fromMeters() > getCurrentRoad().getLengthMeters()
                || proposal.toMeters()
                        > destination.getRoad().getLengthMeters()) {

            throw new IllegalArgumentException(
                    "Movement position exceeds road length."
            );
        }
    }

    @Override
    protected void validateDestination(double newPositionMeters) {
        if (newPositionMeters
                > currentLane.getRoad().getLengthMeters()) {

            throw new IllegalArgumentException(
                    "Cannot move beyond the end of the lane."
            );
        }
    }

    @Override
    protected void afterMovementCommitted(MovementProposal proposal) {
        if (proposal.changesLane()) {
            previousLane = currentLane;
            currentLane = proposal.toLane();
            routeIndex++;
        }

        if (getPositionMeters() >= lengthMeters) {
            previousLane = null;
        }
    }

    public boolean hasRoute() {
        return route != null;
    }

    public Route getRoute() {
        if (!hasRoute()) {
            throw new IllegalStateException(
                    "Vehicle has no assigned route"
            );
        }

        return route;
    }

    public Road getCurrentRoad() {
        return currentLane.getRoad();
    }

    public Road getDestinationRoad() {
        return getRoute().getDestinationRoad();
    }

    public int getRouteIndex() {
        getRoute();
        return routeIndex;
    }

    public boolean hasNextRoad() {
        return hasRoute() && routeIndex < route.size() - 1;
    }

    public Road getNextRoad() {
        if (!hasNextRoad()) {
            throw new IllegalStateException(
                    "Vehicle has no next road"
            );
        }

        return route.getRoadAt(routeIndex + 1);
    }

    public boolean isOnDestinationRoad() {
        return hasRoute() && routeIndex == route.size() - 1;
    }
}