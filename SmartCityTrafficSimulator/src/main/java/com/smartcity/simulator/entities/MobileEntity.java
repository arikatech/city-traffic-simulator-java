package com.smartcity.simulator.entities;

import com.smartcity.simulator.simulation.MovementProposal;
import java.util.Objects;

public abstract class MobileEntity extends SimulationEntity {

    private double positionMeters;

    protected MobileEntity(String id) {
        super(id);
        this.positionMeters = 0.0;
    }

    public double getPositionMeters() {
        return positionMeters;
    }
    
    public void finishStep(double deltaSeconds) {
    if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
        throw new IllegalArgumentException(
                "Time step must be positive and finite."
        );
    }
}

    // Every concrete mobile entity must be able to plan movement.
    public abstract MovementProposal planStep(double deltaSeconds);

    // Apply a validated movement proposal.
    public final void commitStep(MovementProposal proposal) {
        Objects.requireNonNull(proposal);

        if (!getId().equals(proposal.entityId())) {
            throw new IllegalArgumentException("Proposal belongs to a different entity.");
        }

        if (Double.compare(positionMeters, proposal.fromMeters()) != 0) {
            throw new IllegalArgumentException(
                    "Proposal has an outdated starting position.");
        }

        // Validate all relevant conditions before changing state.
        validateProposal(proposal);

        // Commit the position.
        positionMeters = proposal.toMeters();

        // Let the subclass synchronize its additional movement state.
        afterMovementCommitted(proposal);
    }
    
    protected void validateProposal(MovementProposal proposal) {
        validateDestination(proposal.toMeters());
    }           

    protected void afterMovementCommitted(MovementProposal proposal) {
        // Default: no additional state needs updating.
    }   

    protected abstract void validateDestination(double newPositionMeters);
}