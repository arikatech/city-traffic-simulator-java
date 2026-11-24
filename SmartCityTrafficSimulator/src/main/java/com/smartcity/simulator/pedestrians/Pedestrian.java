package com.smartcity.simulator.pedestrians;

import com.smartcity.simulator.city.Crosswalk;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.simulation.MovementProposal;
import java.util.Objects;

public final class Pedestrian extends MobileEntity {

    private final Crosswalk crosswalk;
    private final double walkingSpeed;

    private PedestrianState state = PedestrianState.WAITING;
    private double waitingSeconds;

    public Pedestrian(
            String id,
            Crosswalk crosswalk,
            double walkingSpeed) {

        super(id);
        this.crosswalk = Objects.requireNonNull(crosswalk);

        if (!Double.isFinite(walkingSpeed) || walkingSpeed <= 0.0) {
            throw new IllegalArgumentException(
                    "Walking speed must be positive and finite."
            );
        }

        this.walkingSpeed = walkingSpeed;
    }

    public Crosswalk getCrosswalk() {
        return crosswalk;
    }

    public PedestrianState getState() {
        return state;
    }

    public double getWaitingSeconds() {
        return waitingSeconds;
    }

    @Override
    public MovementProposal planStep(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
            throw new IllegalArgumentException(
                    "Time step must be positive and finite."
            );
        }

        double from = getPositionMeters();

        if (state == PedestrianState.ARRIVED
                || (state == PedestrianState.WAITING
                    && !crosswalk.getSignal().canProceed())) {

            return new MovementProposal(getId(), from, from);
        }

        return new MovementProposal(
                getId(),
                from,
                Math.min(
                        crosswalk.getLengthMeters(),
                        from + walkingSpeed * deltaSeconds
                )
        );
    }

    @Override
    protected void validateProposal(MovementProposal proposal) {
        super.validateProposal(proposal);

        if (proposal.fromLane() != null
                || proposal.toLane() != null
                || proposal.toMeters() < proposal.fromMeters()) {

            throw new IllegalArgumentException(
                    "Invalid pedestrian movement."
            );
        }

        if (proposal.toMeters() > proposal.fromMeters()
                && !crosswalk.isReservedFor(getId())) {

            throw new IllegalArgumentException(
                    "Crossing admission is missing."
            );
        }
    }

    @Override
    protected void validateDestination(double newPositionMeters) {
        if (newPositionMeters > crosswalk.getLengthMeters()) {
            throw new IllegalArgumentException(
                    "Pedestrian moved beyond the crosswalk."
            );
        }
    }

    @Override
    protected void afterMovementCommitted(MovementProposal proposal) {
        if (getPositionMeters() >= crosswalk.getLengthMeters()) {
            state = PedestrianState.ARRIVED;
        } else if (getPositionMeters() > 0.0) {
            state = PedestrianState.CROSSING;
        }
    }

    @Override
    public void finishStep(double deltaSeconds) {
        super.finishStep(deltaSeconds);

        if (state == PedestrianState.WAITING) {
            waitingSeconds += deltaSeconds;
        }
    }
}