
package com.smartcity.simulator.rules;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.signals.IntersectionController;
import com.smartcity.simulator.simulation.MovementProposal;
import com.smartcity.simulator.vehicles.Vehicle;

import java.util.Objects;

public final class RuleEffectApplier {

    private static final String RULE_CLOSURE_REASON =
            "rule-engine:road-entry";

    private final CityMap city;

    public RuleEffectApplier(CityMap city) {
        this.city = Objects.requireNonNull(city);
    }

    // Clear ONLY closures owned by the rule engine.
    public void clearPreviousRoadClosures() {

        for (Road road : city.getRoads()) {
            road.unblockEntry(RULE_CLOSURE_REASON);
        }
    }

    // Apply this tick's resolved rule decisions.
    public void applyRoadDecisions(RuleEffects.Resolved resolved) {

        Objects.requireNonNull(resolved);

        for (Road road : city.getRoads()) {

            RuleEffects.RoadEntryDecision decision =
                    resolved.roadDecisionFor(road.getId());

            if (decision == RuleEffects.RoadEntryDecision.CLOSE) {
                road.blockEntry(RULE_CLOSURE_REASON);
            } else {
                road.unblockEntry(RULE_CLOSURE_REASON);
            }
        }
    }

    // Transfer priority requests to the domain controllers.
    // RuleEffects is never imported by IntersectionController.
    public void applyPriorityRequests(RuleEffects.Resolved resolved) {

        Objects.requireNonNull(resolved);

        // Every tick starts with a fresh set of rule requests.
        for (Intersection intersection : city.getIntersections()) {
            intersection.getController().clearRulePriorityRequests();
        }

        // Already sorted by priority and registration order.
        for (RuleEffects.PriorityRequest request
                : resolved.priorityRequests()) {

            IntersectionController controller = null;

            for (Intersection intersection : city.getIntersections()) {
                if (intersection.getId()
                        .equals(request.intersectionId())) {

                    controller = intersection.getController();
                    break;
                }
            }

            if (controller == null) {
                throw new IllegalStateException(
                        "Unknown priority intersection: "
                        + request.intersectionId());
            }

            switch (request.kind()) {

                case VEHICLE ->
                    controller.requestRuleVehiclePriority(
                            request.subjectId());

                case PEDESTRIAN ->
                    controller.requestRulePedestrianPriority(
                            request.subjectId());

                case SIGNAL ->
                    controller.requestRuleSignalPhase(
                            request.subjectId(),
                            request.requestedPhase());
            }
        }
    }

    // Constrain movement BEFORE InteractionResolver runs.
    public MovementProposal constrainMovement(
            Vehicle vehicle,
            MovementProposal proposal,
            RuleEffects.Resolved resolved,
            double dt) {

        Objects.requireNonNull(vehicle);
        Objects.requireNonNull(proposal);
        Objects.requireNonNull(resolved);

        if (!Double.isFinite(dt) || dt <= 0) {
            throw new IllegalArgumentException(
                    "Invalid delta seconds");
        }

        if (!vehicle.getId().equals(proposal.entityId())) {
            throw new IllegalArgumentException(
                    "Movement proposal belongs to another vehicle");
        }

        double fraction =
                resolved.speedFractionFor(vehicle.getId());

        if (fraction == 1.0) {
            return proposal;
        }

        // The ordinary baseline is independent of current speed.
        Road currentRoad = proposal.fromLane().getRoad();

        double baseline = Math.min(
                vehicle.getPhysicalMaximumSpeed(),
                currentRoad.getBaseSpeedLimit());

        double maximumDistance = baseline * fraction * dt;

        double from = proposal.fromMeters();

        if (maximumDistance <= 0) {
            return waitAtCurrentPosition(proposal);
        }

        double proposedDistance;

        if (proposal.changesLane()) {

            proposedDistance =
                    currentRoad.getLengthMeters()
                    - from
                    + proposal.toMeters();

        } else {

            proposedDistance = proposal.toMeters() - from;
        }

        if (!Double.isFinite(proposedDistance)
                || proposedDistance < 0) {

            throw new IllegalArgumentException(
                    "Invalid movement proposal distance");
        }

        if (proposedDistance <= maximumDistance) {
            return proposal;
        }

        // Case 1: Remain on the current road.
        if (!proposal.changesLane()) {

            return new MovementProposal(
                    proposal.entityId(),
                    from,
                    from + maximumDistance,
                    proposal.fromLane(),
                    proposal.fromLane());
        }

        // Case 2: Proposed crossing into another road.
        double remaining =
                currentRoad.getLengthMeters() - from;

        if (maximumDistance <= remaining) {

            return new MovementProposal(
                    proposal.entityId(),
                    from,
                    from + maximumDistance,
                    proposal.fromLane(),
                    proposal.fromLane());
        }

        // Enough distance to enter the next road,
        // but less than the original proposed movement.
        return new MovementProposal(
                proposal.entityId(),
                from,
                maximumDistance - remaining,
                proposal.fromLane(),
                proposal.toLane());
    }

    private static MovementProposal waitAtCurrentPosition(
            MovementProposal proposal) {

        return new MovementProposal(
                proposal.entityId(),
                proposal.fromMeters(),
                proposal.fromMeters(),
                proposal.fromLane(),
                proposal.fromLane());
    }
}
