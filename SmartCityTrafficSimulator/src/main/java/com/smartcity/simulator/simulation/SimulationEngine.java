
package com.smartcity.simulator.simulation;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;

import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.vehicles.Vehicle;
import com.smartcity.simulator.vehicles.EmergencyVehicle;

import com.smartcity.simulator.routing.RouteReplanner;
import com.smartcity.simulator.statistics.StatisticsCollector;
import com.smartcity.simulator.statistics.StatisticsSnapshot;

import com.smartcity.simulator.environment.EnvironmentState;
import com.smartcity.simulator.environment.Weather;

import com.smartcity.simulator.events.EventContext;
import com.smartcity.simulator.events.EventScheduler;
import com.smartcity.simulator.events.SimulationEvent;

import com.smartcity.simulator.scenarios.TrafficSpawner;
import com.smartcity.simulator.scenarios.PedestrianSpawner;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.pedestrians.PedestrianState;

import com.smartcity.simulator.rules.RuleEngine;
import com.smartcity.simulator.rules.RuleEffects;
import com.smartcity.simulator.rules.RuleEffectApplier;
import com.smartcity.simulator.rules.Rule;
import com.smartcity.simulator.rules.InvalidRuleException;
import com.smartcity.simulator.rules.builder.CompiledRule;
import com.smartcity.simulator.rules.builder.RuleDraft;
import com.smartcity.simulator.rules.builder.TargetCategory;
import com.smartcity.simulator.rules.management.RuleCommand;
import com.smartcity.simulator.rules.management.RuleCommandResult;
import com.smartcity.simulator.rules.management.RuleDraftDescriptions;
import com.smartcity.simulator.rules.management.RuleSummary;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.IdentityHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SimulationEngine {

    private final SimulationClock clock;
    private final CityMap city;
    private final RouteReplanner routeReplanner;

    private final EnvironmentState environment =
            new EnvironmentState();

    private final EventScheduler eventScheduler;

    private final InteractionResolver interactionResolver =
            new InteractionResolver(3.0);

    // Stage 7.4: committed-tick statistics, never updated by the UI.
    private final StatisticsCollector statistics = new StatisticsCollector();

    public StatisticsSnapshot getStatisticsSnapshot() {
        return statistics.snapshot();
    }

    // Stage 5: Rule system.
    private final RuleEngine ruleEngine;
    private final RuleEffectApplier ruleEffectApplier;

    private RuleEffects.Resolved lastResolvedRuleEffects;

    /** Rule decisions observed at the PRE-MOVEMENT boundary of the last tick. */
    public record VehicleRuleObservation(double evaluatedAtSeconds,
            double baselineCapMetersPerSecond, double permittedCapMetersPerSecond,
            List<String> matchedRuleIds) {
        public VehicleRuleObservation {
            matchedRuleIds = List.copyOf(matchedRuleIds);
        }
    }

    private Map<String, VehicleRuleObservation> lastVehicleRuleObservations = Map.of();

    public Map<String, VehicleRuleObservation> getLastVehicleRuleObservations() {
        return lastVehicleRuleObservations;
    }

    /**
     * Measured speed over the last COMMITTED logical tick, in metres/second.
     * A vehicle's configured speed (Vehicle.getSpeedMetersPerSecond()) is a
     * cruising request; it must not be mistaken for the realised speed when
     * rules, signals or vehicle following restrict movement.
     *
     * Vehicles spawned after the latest tick do not have a measurement yet.
     */
    private Map<String, Double> lastCommittedVehicleSpeeds = Map.of();

    public Map<String, Double> getLastCommittedVehicleSpeeds() {
        return lastCommittedVehicleSpeeds;
    }

    private static double committedDistanceMeters(MovementProposal result) {
        if (!result.changesLane()) {
            return Math.max(0.0, result.toMeters() - result.fromMeters());
        }
        // A transition can cross only one road per tick. The new road's
        // coordinate starts at zero, so plain (to - from) is incorrect.
        double remainingOnPreviousRoad = result.fromLane().getRoad()
                .getLengthMeters() - result.fromMeters();
        return Math.max(0.0, remainingOnPreviousRoad + result.toMeters());
    }

    /**
     * Observe exactly the same pre-movement world as rule evaluation.
     * This uses separate, disposable RuleEffects so inspectors cannot add to
     * the real decisions. Condition and Action contracts are side-effect-free.
     * A future rule-engine match callback could avoid this bounded second pass.
     */
    private void captureVehicleRuleObservations() {
        Map<String, VehicleRuleObservation> observations = new LinkedHashMap<>();
        for (MobileEntity entity : city.getMobileEntities()) {
            if (!(entity instanceof Vehicle vehicle)) continue;
            String id = vehicle.getId();
            double baseline = Math.min(vehicle.getPhysicalMaximumSpeed(),
                    vehicle.getCurrentRoad().getBaseSpeedLimit());
            double fraction = lastResolvedRuleEffects.speedFractionFor(id);
            double permitted = lastResolvedRuleEffects.isStopped(id)
                    ? 0.0 : Math.min(baseline, baseline * fraction);
            List<String> matching = new ArrayList<>();
            for (Rule<Vehicle> rule : ruleEngine.getVehicleRules()) {
                if (rule.evaluate(vehicle, lastTickContext, new RuleEffects())) {
                    matching.add(rule.getId());
                }
            }
            observations.put(id, new VehicleRuleObservation(
                    clock.getElapsedSeconds(), baseline, permitted, matching));
        }
        lastVehicleRuleObservations = Map.copyOf(observations);
    }

    private TrafficSpawner trafficSpawner;
    private PedestrianSpawner pedestrianSpawner;
    private SimulationContext lastTickContext;

    // First observation at a committed simulation tick (NOT a render frame).
    // Identity keys are used so a new vehicle with a reused ID is independent.
    private final Map<Vehicle, Double> tripStartSeconds =
            new IdentityHashMap<>();

    // Stage 7.2: immutable arrival facts retained for Stage 7.4 statistics.
    private final List<CompletedTrip> completedTrips = new ArrayList<>();

    private static final double ARRIVAL_TOLERANCE_METERS = 1e-7;

    // Stage 6.4: GUI commands are applied only at an explicit lifecycle
    // boundary, never from a rule-editor event handler.
    public enum RunState { READY, RUNNING, PAUSED }

    private RunState runState = RunState.READY;
    private boolean insideStep;
    private final Deque<RuleCommand> pendingRuleCommands = new ArrayDeque<>();
    private final List<RuleCommandResult> commandResults = new ArrayList<>();
    private final Map<String, RuleDraft> managedRuleDrafts = new LinkedHashMap<>();

    public SimulationEngine(
            SimulationClock clock,
            CityMap city) {

        this.clock = Objects.requireNonNull(clock);
        this.city = Objects.requireNonNull(city);

        this.routeReplanner = new RouteReplanner(this.city);

        this.eventScheduler = new EventScheduler(
                new EventContext(this.city, environment));

        // Stage 5 initialization.
        this.ruleEngine = new RuleEngine(this.city);

        this.ruleEffectApplier =
                new RuleEffectApplier(this.city);

        this.lastResolvedRuleEffects =
                new RuleEffects().resolve();
    }

    public void step() {
        if (insideStep) {
            throw new IllegalStateException("Reentrant simulation step");
        }
        insideStep = true;
        try {
            // Phase -1: atomically apply the queued GUI requests before
            // events, observation capture and rule evaluation at time t.
            applyPendingRuleCommandsInternal();

        double dt = clock.getDeltaSeconds();

        // ========================================
        // PHASE 0A: SCHEDULED EVENTS
        // ========================================

        eventScheduler.processAt(clock.getElapsedSeconds());

        // ========================================
        // PHASE 0B: PREPARE RULE OBSERVATIONS
        // ========================================

        // Previous rule closures are transient.
        // Incident-owned closures are not removed.
        ruleEffectApplier.clearPreviousRoadClosures();

        // All conditions read the same pre-movement snapshot.
        lastTickContext = SimulationContext.capture(
                clock,
                city,
                environment.getWeather()
        );

        // ========================================
        // PHASE 0C: EVALUATE AND RESOLVE RULES
        // ========================================

        RuleEffects effects =
                ruleEngine.evaluate(lastTickContext);

        lastResolvedRuleEffects = effects.resolve();

        // Publish per-vehicle rule facts BEFORE any route/movement commit.
        captureVehicleRuleObservations();

        // Apply road decisions before route planning.
        ruleEffectApplier.applyRoadDecisions(
                lastResolvedRuleEffects);

        // Queue safe requests for intersection controllers.
        ruleEffectApplier.applyPriorityRequests(
                lastResolvedRuleEffects);

        // ========================================
        // PHASE 1: MOVEMENT PROPOSALS
        // ========================================

        List<MobileEntity> entities = city.getMobileEntities();

        // Record when each vehicle first enters a logical tick. Manual spawns
        // are observed at their actual starting tick; automatic spawns are
        // first observed in the tick AFTER the spawner creates them.
        for (MobileEntity entity : entities) {
            if (entity instanceof Vehicle vehicle) {
                tripStartSeconds.putIfAbsent(
                        vehicle, clock.getElapsedSeconds());
            }
        }

        // Capture positions and pedestrian states BEFORE planning starts.
        StatisticsCollector.TickStart statisticsStart =
                statistics.captureBeforeProposals(entities);

        List<MovementProposal> proposals = new ArrayList<>();

        for (MobileEntity entity : entities) {

            MovementProposal proposal;

            if (entity instanceof Vehicle vehicle
                    && !routeReplanner.ensureUsableRoute(vehicle)) {

                // No route available: vehicle waits.
                proposal = new MovementProposal(
                        vehicle.getId(),
                        vehicle.getPositionMeters(),
                        vehicle.getPositionMeters(),
                        vehicle.getCurrentLane(),
                        vehicle.getCurrentLane()
                );

            } else {

                // Normal subtype-specific behavior.
                proposal = entity.planStep(dt);
            }

            // Apply rule speed/stop constraints before
            // the interaction resolver examines the proposal.
            if (entity instanceof Vehicle vehicle) {

                proposal = ruleEffectApplier.constrainMovement(
                        vehicle,
                        proposal,
                        lastResolvedRuleEffects,
                        dt);
            }

            proposals.add(proposal);
        }

        // ========================================
        // PHASE 2: SAFETY RESOLUTION
        // ========================================

        List<MovementProposal> resolved =
                interactionResolver.resolve(
                        entities,
                        proposals,
                        city.getIntersections()
                );

        // ========================================
        // PHASE 3: COMMIT ACCEPTED MOVEMENTS
        // ========================================

        // Observe actual admitted movement, not the vehicle's requested cruise
        // speed and not its pre-safety movement proposal.
        Map<String, Double> committedSpeeds = new LinkedHashMap<>();
        for (int i = 0; i < entities.size(); i++) {
            MobileEntity entity = entities.get(i);
            MovementProposal accepted = resolved.get(i);
            entity.commitStep(accepted);
            if (entity instanceof Vehicle) {
                committedSpeeds.put(entity.getId(),
                        committedDistanceMeters(accepted) / dt);
            }
        }
        lastCommittedVehicleSpeeds = Map.copyOf(committedSpeeds);

        for (MobileEntity entity : entities) {
            entity.finishStep(dt);
        }

        // ========================================
        // PHASE 3B: COMPLETED TRIPS (AFTER COMMIT)
        // ========================================

        // A car only leaves when the COMMITTED position has reached the end
        // of the FINAL route road, never when merely queued at a red light.
        // Do not remove entities while iterating the proposal/commit lists.
        finishArrivedTrips(clock.getElapsedSeconds() + dt);

        // Count only successful committed outcomes. Vehicles that departed
        // the city this tick cannot inflate the current queue length.
        statistics.recordCommittedTick(
                statisticsStart, city.getMobileEntities(), dt);

        // Stats see committed ARRIVED transitions before pedestrians leave the city.
        // A finished crossing can no longer reserve the conflict zone next tick.
        retireArrivedPedestrians();

        // ========================================
        // PHASE 4: SIGNAL COORDINATION
        // ========================================

        for (Intersection intersection : city.getIntersections()) {
            intersection.getController().advance(dt);
        }

        // ========================================
        // PHASE 5: SCENARIO SPAWNING
        // ========================================

        // Spawning happens after the current tick's
        // restrictions are applied and movement is committed.
        //
        // New vehicles start moving on the following tick.
        if (trafficSpawner != null) {
            trafficSpawner.advance(dt);
        }
        if (pedestrianSpawner != null) {
            pedestrianSpawner.advance(dt);
        }

        // ========================================
        // PHASE 6: ADVANCE THE SIMULATION CLOCK
        // ========================================

        clock.advance();
        } finally {
            insideStep = false;
        }
    }

    /**
     * Retire arriving vehicles only after all proposals have been resolved,
     * committed and finishStep() has run. A vehicle on an intermediate road
     * stays even if it reaches that road's end.
     */
    private void finishArrivedTrips(double arrivalSeconds) {
        List<Vehicle> arrivals = new ArrayList<>();

        for (MobileEntity entity : city.getMobileEntities()) {
            if (!(entity instanceof Vehicle vehicle)
                    || !vehicle.isOnDestinationRoad()) {
                continue;
            }

            double endpoint = vehicle.getCurrentRoad().getLengthMeters();
            if (vehicle.getPositionMeters() + ARRIVAL_TOLERANCE_METERS
                    < endpoint) {
                continue;
            }

            arrivals.add(vehicle);
        }

        // Keep the arrival records and membership removals together. The
        // copied list avoids modifying the entity collection mid-iteration.
        for (Vehicle vehicle : arrivals) {
            if (!city.removeVehicle(vehicle)) {
                throw new IllegalStateException(
                        "Arriving vehicle was missing from CityMap: "
                                + vehicle.getId());
            }
            double departure = tripStartSeconds.getOrDefault(
                    vehicle, clock.getElapsedSeconds());
            CompletedTrip completed = new CompletedTrip(
                    vehicle.getId(),
                    vehicle.getClass().getSimpleName(),
                    departure,
                    arrivalSeconds,
                    vehicle instanceof EmergencyVehicle);
            completedTrips.add(completed);
            statistics.recordCompletedTrip(completed);
            tripStartSeconds.remove(vehicle);
        }
    }

    private void retireArrivedPedestrians() {
        for (MobileEntity entity : city.getMobileEntities()) {
            if (entity instanceof Pedestrian pedestrian
                    && pedestrian.getState() == PedestrianState.ARRIVED) {
                if (!city.removePedestrian(pedestrian)) {
                    throw new IllegalStateException(
                            "Arriving pedestrian missing from CityMap: "
                                    + pedestrian.getId());
                }
            }
        }
    }

    /** A defensive immutable view of all trips completed in this run. */
    public List<CompletedTrip> getCompletedTrips() {
        return List.copyOf(completedTrips);
    }

    public int getCompletedTripCount() {
        return completedTrips.size();
    }

    public void installTrafficSpawner(TrafficSpawner spawner) {

        Objects.requireNonNull(spawner);

        if (clock.getTickCount() != 0
                || trafficSpawner != null) {

            throw new IllegalStateException(
                    "Install one spawner before the first tick");
        }

        this.trafficSpawner = spawner;
    }

    /** Install the bounded pedestrian generator once, before any logical tick. */
    public void installPedestrianSpawner(PedestrianSpawner spawner) {
        Objects.requireNonNull(spawner, "spawner");
        if (clock.getTickCount() != 0 || pedestrianSpawner != null) {
            throw new IllegalStateException(
                    "Install one pedestrian spawner before the first tick");
        }
        this.pedestrianSpawner = spawner;
    }

    // ========================================
    // STAGE 6.4: RULE COMMAND LIFECYCLE
    // ========================================

    /** Start means that the external UI timer is allowed to call step(). */
    public void startRunning() {
        if (insideStep) throw new IllegalStateException("Cannot start during a tick");
        runState = RunState.RUNNING;
    }

    /** Stopping the external timer is the caller's responsibility. */
    public void pauseRunning() {
        if (insideStep) throw new IllegalStateException("Cannot pause during a tick");
        runState = RunState.PAUSED;
    }

    public RunState getRunState() {
        return runState;
    }

    /** Only call this and step() from the same serialized simulation thread. */
    public void enqueueRuleCommand(RuleCommand command) {
        pendingRuleCommands.addLast(Objects.requireNonNull(command));
    }

    public int getPendingRuleCommandCount() {
        return pendingRuleCommands.size();
    }

    /**
     * Administrative boundary when READY or PAUSED: commands take effect
     * without moving entities, evaluating rules, or advancing the clock.
     * Their effects become observable at the next normal step().
     */
    public int applyPendingRuleCommandsWhilePaused() {
        if (runState == RunState.RUNNING || insideStep) {
            throw new IllegalStateException(
                    "Pause the simulation before applying commands without a tick");
        }
        return applyPendingRuleCommandsInternal();
    }

    private int applyPendingRuleCommandsInternal() {
        int processed = 0;
        while (!pendingRuleCommands.isEmpty()) {
            RuleCommand command = pendingRuleCommands.removeFirst();
            processed++;
            try {
                applyRuleCommand(command);
                commandResults.add(new RuleCommandResult(
                        command.id(), operationName(command), true,
                        "Applied " + operationName(command) + " for " + command.id()));
            } catch (IllegalArgumentException exception) {
                commandResults.add(new RuleCommandResult(
                        command.id(), operationName(command), false,
                        exception.getMessage()));
            }
        }
        return processed;
    }

    private static String operationName(RuleCommand command) {
        if (command instanceof RuleCommand.Add) return "create";
        if (command instanceof RuleCommand.Replace) return "edit";
        if (command instanceof RuleCommand.SetEnabled) return "enable/disable";
        return "delete";
    }

    private void applyRuleCommand(RuleCommand command) {
        switch (command) {
            case RuleCommand.Add add -> {
                ensureMatchingId(add.id(), add.compiled());
                ensureMatchingCategory(add.draft(), add.compiled());
                registerCompiled(add.compiled());
                managedRuleDrafts.put(add.id(), add.draft());
            }
            case RuleCommand.Replace replacement -> {
                ensureMatchingId(replacement.id(), replacement.compiled());
                ensureMatchingCategory(replacement.draft(), replacement.compiled());
                if (!managedRuleDrafts.containsKey(replacement.id())) {
                    throw new InvalidRuleException(
                            "Only rules created in the visual editor can be edited here");
                }
                replaceCompiled(replacement.compiled());
                managedRuleDrafts.put(replacement.id(), replacement.draft());
            }
            case RuleCommand.SetEnabled change -> {
                ruleEngine.setEnabled(change.id(), change.enabled());
                RuleDraft old = managedRuleDrafts.get(change.id());
                if (old != null) {
                    managedRuleDrafts.put(change.id(), new RuleDraft(
                            old.name(), old.target(), old.scope(), old.scopeId(),
                            old.firstCondition(), old.secondCondition(), old.connector(),
                            old.action(), old.priority(), change.enabled()));
                }
            }
            case RuleCommand.Delete delete -> {
                if (!ruleEngine.remove(delete.id())) {
                    throw new InvalidRuleException("Unknown rule ID: " + delete.id());
                }
                managedRuleDrafts.remove(delete.id());
            }
        }
    }

    private static void ensureMatchingId(String id, CompiledRule compiled) {
        String compiledId = switch (compiled) {
            case CompiledRule.VehicleEntry entry -> entry.rule().getId();
            case CompiledRule.PedestrianEntry entry -> entry.rule().getId();
            case CompiledRule.LightEntry entry -> entry.rule().getId();
            case CompiledRule.RoadEntry entry -> entry.rule().getId();
        };
        if (!id.equals(compiledId)) {
            throw new InvalidRuleException("Command ID does not match compiled rule ID");
        }
    }

    private static void ensureMatchingCategory(RuleDraft draft, CompiledRule compiled) {
        TargetCategory compiledCategory = switch (compiled) {
            case CompiledRule.VehicleEntry ignored -> TargetCategory.VEHICLE;
            case CompiledRule.PedestrianEntry ignored -> TargetCategory.PEDESTRIAN;
            case CompiledRule.LightEntry ignored -> TargetCategory.TRAFFIC_LIGHT;
            case CompiledRule.RoadEntry ignored -> TargetCategory.ROAD;
        };
        if (draft.target() != compiledCategory) {
            throw new InvalidRuleException("Draft target does not match compiled rule category");
        }
    }

    private void registerCompiled(CompiledRule compiled) {
        switch (compiled) {
            case CompiledRule.VehicleEntry entry -> ruleEngine.addVehicleRule(entry.rule());
            case CompiledRule.PedestrianEntry entry -> ruleEngine.addPedestrianRule(entry.rule());
            case CompiledRule.LightEntry entry -> ruleEngine.addTrafficLightRule(entry.rule());
            case CompiledRule.RoadEntry entry -> ruleEngine.addRoadRule(entry.rule());
        }
    }

    private void replaceCompiled(CompiledRule compiled) {
        switch (compiled) {
            case CompiledRule.VehicleEntry entry -> ruleEngine.replaceWithVehicleRule(entry.rule());
            case CompiledRule.PedestrianEntry entry -> ruleEngine.replaceWithPedestrianRule(entry.rule());
            case CompiledRule.LightEntry entry -> ruleEngine.replaceWithTrafficLightRule(entry.rule());
            case CompiledRule.RoadEntry entry -> ruleEngine.replaceWithRoadRule(entry.rule());
        }
    }

    /** Each acknowledgement can be handled once by the UI. */
    public List<RuleCommandResult> drainRuleCommandResults() {
        List<RuleCommandResult> result = List.copyOf(commandResults);
        commandResults.clear();
        return result;
    }

    /** Null for Java-created rules that have no recoverable editor draft. */
    public RuleDraft getManagedRuleDraft(String id) {
        return managedRuleDrafts.get(id);
    }

    /** Immutable rows built from the actual registered rule groups. */
    public List<RuleSummary> getRuleSummaries() {
        List<RuleSummary> rows = new ArrayList<>();
        for (Rule<?> rule : ruleEngine.getVehicleRules()) {
            rows.add(ruleSummary(rule, TargetCategory.VEHICLE));
        }
        for (Rule<?> rule : ruleEngine.getPedestrianRules()) {
            rows.add(ruleSummary(rule, TargetCategory.PEDESTRIAN));
        }
        for (Rule<?> rule : ruleEngine.getTrafficLightRules()) {
            rows.add(ruleSummary(rule, TargetCategory.TRAFFIC_LIGHT));
        }
        for (Rule<?> rule : ruleEngine.getRoadRules()) {
            rows.add(ruleSummary(rule, TargetCategory.ROAD));
        }
        return List.copyOf(rows);
    }

    private RuleSummary ruleSummary(Rule<?> rule, TargetCategory target) {
        RuleDraft draft = managedRuleDrafts.get(rule.getId());
        return new RuleSummary(
                rule.getId(), rule.getName(), target,
                draft == null ? "Java-defined scope" : RuleDraftDescriptions.scope(draft),
                draft == null ? "Java-defined condition" : RuleDraftDescriptions.expression(draft),
                draft == null ? "Java-defined action" : RuleDraftDescriptions.action(draft),
                rule.getPriority(), rule.isEnabled(), draft != null);
    }

    // Stage 5: Rule registration and inspection.

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    public RuleEffects.Resolved getLastResolvedRuleEffects() {
        return lastResolvedRuleEffects;
    }

    // Existing Stage 1-4 APIs.

    public double getElapsedSeconds() {
        return clock.getElapsedSeconds();
    }

    public long getTickCount() {
        return clock.getTickCount();
    }

    public Weather getWeather() {
        return environment.getWeather();
    }

    public void scheduleEvent(SimulationEvent event) {
        eventScheduler.schedule(event);
    }

    public SimulationContext getLastTickContext() {

        if (lastTickContext == null) {
            throw new IllegalStateException(
                    "No simulation tick has been processed yet");
        }

        return lastTickContext;
    }
}