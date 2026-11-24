package com.smartcity.simulator.vehicles;

import com.smartcity.simulator.city.BusStop;
import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.simulation.MovementProposal;
import java.util.Objects;

public final class Bus extends Vehicle {

    private final BusStop stop;
    private final double dwellSeconds;

    private BusState state = BusState.APPROACHING;
    private double remainingDwellSeconds;
    private boolean arrivedThisStep;

    public Bus(
            String id,
            Lane startingLane,
            BusStop stop,
            double dwellSeconds) {

        super(id, startingLane, 12.0);

        this.stop = Objects.requireNonNull(
                stop, "Bus stop cannot be null."
        );

        if (stop.getLane() != startingLane) {
            throw new IllegalArgumentException(
                    "This version requires the stop on the starting lane."
            );
        }

        if (!Double.isFinite(dwellSeconds) || dwellSeconds <= 0.0) {
            throw new IllegalArgumentException(
                    "Dwell duration must be positive and finite."
            );
        }

        this.dwellSeconds = dwellSeconds;
    }
    @Override
    public double getPhysicalMaximumSpeed() {
        return 20.0;
    }

    public BusState getState() {
        return state;
    }

    public double getRemainingDwellSeconds() {
        return remainingDwellSeconds;
    }
    
    @Override
    public MovementProposal planStep(double deltaSeconds) {
        MovementProposal proposed = super.planStep(deltaSeconds);

        if (state == BusState.DWELLING) {
            return new MovementProposal(
                    getId(),
                    getPositionMeters(),
                    getPositionMeters(),
                    getCurrentLane(),
                    getCurrentLane()
            );
        }

        if (state == BusState.APPROACHING
                && getCurrentLane() == stop.getLane()
                && getPositionMeters() <= stop.getPositionMeters()
                && (proposed.changesLane()
                    || proposed.toMeters() >= stop.getPositionMeters())) {

            return new MovementProposal(
                    getId(),
                    getPositionMeters(),
                    stop.getPositionMeters(),
                    getCurrentLane(),
                    getCurrentLane()
            );
        }

        return proposed;
    }
    
    @Override
    protected void afterMovementCommitted(MovementProposal proposal) {
        super.afterMovementCommitted(proposal);

        if (state == BusState.APPROACHING
                && getCurrentLane() == stop.getLane()
                && Double.compare(
                        getPositionMeters(),
                        stop.getPositionMeters()) == 0) {

            state = BusState.DWELLING;
            remainingDwellSeconds = dwellSeconds;
            arrivedThisStep = true;
        }
    }
    
    @Override
    public void finishStep(double deltaSeconds) {
        super.finishStep(deltaSeconds);

        if (state != BusState.DWELLING) {
            return;
        }

        if (arrivedThisStep) {
            arrivedThisStep = false;
            return;
        }

        remainingDwellSeconds = Math.max(
                0.0,
                remainingDwellSeconds - deltaSeconds
        );

        if (remainingDwellSeconds == 0.0) {
            state = BusState.DEPARTING;
        }
    }
}