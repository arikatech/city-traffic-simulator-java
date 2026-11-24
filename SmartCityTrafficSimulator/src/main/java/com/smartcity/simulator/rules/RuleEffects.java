
package com.smartcity.simulator.rules;

import com.smartcity.simulator.signals.SignalState;
import java.util.*;

public final class RuleEffects {

    public enum RoadEntryDecision {
        CLOSE, ALLOW
    }

    public enum PriorityKind {
        VEHICLE, PEDESTRIAN, SIGNAL
    }

    public record Match(String ruleId, int priority) {
        public Match {
            Objects.requireNonNull(ruleId);
        }
    }

    // A priority request with its source rule metadata.
    public record PriorityRequest(
            PriorityKind kind,
            String intersectionId,
            String subjectId,
            SignalState requestedPhase,
            String ruleId,
            int priority,
            long registrationOrder) {

        public PriorityRequest {
            Objects.requireNonNull(kind);
            requireId(intersectionId);
            requireId(subjectId);
            requireId(ruleId);

            if ((kind == PriorityKind.SIGNAL)
                    != (requestedPhase != null)) {
                throw new IllegalArgumentException(
                        "Phase is required only for signal requests");
            }
        }
    }

    // A request to close or allow road entry.
    public record RoadEntryRequest(
            String roadId,
            RoadEntryDecision decision,
            String ruleId,
            int priority,
            long registrationOrder) {

        public RoadEntryRequest {
            requireId(roadId);
            Objects.requireNonNull(decision);
            requireId(ruleId);
        }
    }

    // Immutable result of reducing all requests.
    public record Resolved(
            Map<String, Double> speedFractions,
            Map<String, RoadEntryDecision> roadDecisions,
            List<PriorityRequest> priorityRequests) {

        public Resolved {
            speedFractions = Map.copyOf(speedFractions);
            roadDecisions = Map.copyOf(roadDecisions);
            priorityRequests = List.copyOf(priorityRequests);
        }

        public double speedFractionFor(String vehicleId) {
            return speedFractions.getOrDefault(vehicleId, 1.0);
        }

        public boolean isStopped(String vehicleId) {
            return speedFractionFor(vehicleId) == 0.0;
        }

        // null means no road-entry rule matched this tick.
        public RoadEntryDecision roadDecisionFor(String roadId) {
            return roadDecisions.get(roadId);
        }

        public List<PriorityRequest> requestsAt(String intersectionId) {
            return priorityRequests.stream()
                    .filter(request ->
                            request.intersectionId().equals(intersectionId))
                    .toList();
        }
    }

    private record Source(
            String ruleId,
            int priority,
            long registrationOrder) {
    }

    private final List<Match> matches = new ArrayList<>();

    private final Map<String, Long> registrationOrders =
            new HashMap<>();

    private final Map<String, Double> speedFractions =
            new LinkedHashMap<>();

    private final List<RoadEntryRequest> roadRequests =
            new ArrayList<>();

    private final List<PriorityRequest> priorityRequests =
            new ArrayList<>();

    private Source activeSource;

    // Called internally by RuleEngine.
    void registerRuleOrder(String ruleId, long order) {
        requireId(ruleId);

        if (order < 0) {
            throw new IllegalArgumentException(
                    "Negative registration order");
        }

        registrationOrders.putIfAbsent(ruleId, order);
    }

    // Called internally by Rule<T>.
    void collectForRule(
            String id,
            int priority,
            Runnable collection) {

        requireId(id);
        Objects.requireNonNull(collection);

        if (activeSource != null) {
            throw new IllegalStateException(
                    "Nested rule action collection is not supported");
        }

        activeSource = new Source(
                id,
                priority,
                registrationOrders.getOrDefault(
                        id, Long.MAX_VALUE));

        try {
            collection.run();
        } finally {
            activeSource = null;
        }
    }

    void recordMatch(String id, int priority) {
        matches.add(new Match(id, priority));
    }

    public List<Match> getMatches() {
        return List.copyOf(matches);
    }

    private Source source() {
        if (activeSource == null) {
            throw new IllegalStateException(
                    "Effects can only be collected by an evaluating rule");
        }

        return activeSource;
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "An ID is required");
        }

        return id;
    }

    // ==========================================
    // SPEED AND STOP REQUESTS
    // ==========================================

    public void limitVehicleSpeed(
            String vehicleId, double fraction) {

        source();
        requireId(vehicleId);

        if (!Double.isFinite(fraction)
                || fraction <= 0.0
                || fraction > 1.0) {

            throw new InvalidRuleException(
                    "Speed fraction must be greater than 0 and at most 1");
        }

        speedFractions.merge(
                vehicleId, fraction, Math::min);
    }

    public void stopVehicle(String vehicleId) {

        source();
        requireId(vehicleId);

        // Stop is represented by a speed fraction of zero.
        speedFractions.merge(
                vehicleId, 0.0, Math::min);
    }

    // ==========================================
    // PRIORITY AND SIGNAL REQUESTS
    // ==========================================

    public void requestVehiclePriority(
            String vehicleId, String intersectionId) {

        Source s = source();

        priorityRequests.add(new PriorityRequest(
                PriorityKind.VEHICLE,
                intersectionId,
                vehicleId,
                null,
                s.ruleId(),
                s.priority(),
                s.registrationOrder()));
    }

    public void requestPedestrianPriority(
            String pedestrianId, String intersectionId) {

        Source s = source();

        priorityRequests.add(new PriorityRequest(
                PriorityKind.PEDESTRIAN,
                intersectionId,
                pedestrianId,
                null,
                s.ruleId(),
                s.priority(),
                s.registrationOrder()));
    }

    public void requestSignalPhase(
            String incomingRoadId,
            String intersectionId,
            SignalState phase) {

        Source s = source();

        if (phase != SignalState.GREEN
                && phase != SignalState.RED) {

            throw new InvalidRuleException(
                    "Only GREEN or RED can be requested; "
                    + "YELLOW is controller-managed");
        }

        priorityRequests.add(new PriorityRequest(
                PriorityKind.SIGNAL,
                intersectionId,
                incomingRoadId,
                phase,
                s.ruleId(),
                s.priority(),
                s.registrationOrder()));
    }

    // ==========================================
    // ROAD ENTRY REQUESTS
    // ==========================================

    public void requestRoadEntry(
            String roadId, RoadEntryDecision decision) {

        Source s = source();

        roadRequests.add(new RoadEntryRequest(
                roadId,
                decision,
                s.ruleId(),
                s.priority(),
                s.registrationOrder()));
    }

    // ==========================================
    // DETERMINISTIC EFFECT REDUCTION
    // ==========================================

    public Resolved resolve() {

        Map<String, RoadEntryRequest> winners =
                new LinkedHashMap<>();

        for (RoadEntryRequest request : roadRequests) {

            RoadEntryRequest old =
                    winners.get(request.roadId());

            if (old == null || roadRequestWins(request, old)) {
                winners.put(request.roadId(), request);
            }
        }

        Map<String, RoadEntryDecision> decisions =
                new LinkedHashMap<>();

        winners.forEach((roadId, request) ->
                decisions.put(roadId, request.decision()));

        // All priority requests remain available to the
        // controllers, ordered by priority and creation order.
        List<PriorityRequest> sorted =
                new ArrayList<>(priorityRequests);

        sorted.sort(
                Comparator.comparingInt(PriorityRequest::priority)
                        .reversed()
                        .thenComparingLong(
                                PriorityRequest::registrationOrder)
                        .thenComparing(PriorityRequest::ruleId)
                        .thenComparing(PriorityRequest::subjectId)
                        .thenComparing(PriorityRequest::kind));

        return new Resolved(
                speedFractions,
                decisions,
                sorted);
    }

    private static boolean roadRequestWins(
            RoadEntryRequest next,
            RoadEntryRequest old) {

        // Higher user priority wins.
        if (next.priority() != old.priority()) {
            return next.priority() > old.priority();
        }

        // Equal priority: closure wins.
        if (next.decision() != old.decision()) {
            return next.decision() == RoadEntryDecision.CLOSE;
        }

        // Equal priority and decision: earlier rule wins.
        if (next.registrationOrder() != old.registrationOrder()) {
            return next.registrationOrder()
                    < old.registrationOrder();
        }

        return next.ruleId().compareTo(old.ruleId()) < 0;
    }
}
