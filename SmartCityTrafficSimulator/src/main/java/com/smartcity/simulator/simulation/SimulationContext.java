
package com.smartcity.simulator.simulation;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Lane;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.vehicles.Vehicle;

import java.util.*;

public final class SimulationContext {

    // Snapshot of one lane.
    public record LaneFacts(
            String id,
            String roadId,
            boolean locallyOpen,
            boolean entryAllowed,
            List<String> blockageReasons) {

        public LaneFacts {
            Objects.requireNonNull(id);
            Objects.requireNonNull(roadId);
            blockageReasons = List.copyOf(blockageReasons);
        }
    }

    // Snapshot of one road.
    public record RoadFacts(
            String id,
            boolean roadEntryOpen,
            boolean entryAvailable,
            int vehicleCount,
            List<String> blockageReasons,
            List<LaneFacts> lanes) {

        public RoadFacts {
            Objects.requireNonNull(id);
            blockageReasons = List.copyOf(blockageReasons);
            lanes = List.copyOf(lanes);

            if (vehicleCount < 0) {
                throw new IllegalArgumentException(
                        "Negative vehicle count");
            }
        }
    }

    private final double elapsedSeconds;
    private final long tickCount;
    private final Weather weather;

    private final Map<String, RoadFacts> roadsById;
    private final Map<String, LaneFacts> lanesById;

    // Additional observations needed by the rule engine.
    private final RuleObservations ruleObservations;

    private SimulationContext(
            double elapsedSeconds,
            long tickCount,
            Weather weather,
            Map<String, RoadFacts> roadsById,
            Map<String, LaneFacts> lanesById,
            RuleObservations ruleObservations) {

        if (!Double.isFinite(elapsedSeconds)
                || elapsedSeconds < 0) {
            throw new IllegalArgumentException(
                    "Invalid simulation time");
        }

        this.elapsedSeconds = elapsedSeconds;
        this.tickCount = tickCount;
        this.weather = Objects.requireNonNull(weather);

        this.roadsById = Collections.unmodifiableMap(
                new LinkedHashMap<>(roadsById));

        this.lanesById = Collections.unmodifiableMap(
                new LinkedHashMap<>(lanesById));

        this.ruleObservations =
                Objects.requireNonNull(ruleObservations);
    }

    public static SimulationContext capture(
            SimulationClock clock,
            CityMap city,
            Weather weather) {

        Objects.requireNonNull(clock);
        Objects.requireNonNull(city);
        Objects.requireNonNull(weather);

        // Count vehicles on their current roads.
        Map<String, Integer> vehicleCounts = new HashMap<>();

        for (MobileEntity entity : city.getMobileEntities()) {

            if (entity instanceof Vehicle vehicle) {

                Road currentRoad = vehicle.getCurrentRoad();

                if (currentRoad != null) {
                    vehicleCounts.merge(
                            currentRoad.getId(),
                            1,
                            Integer::sum);
                }
            }
        }

        Map<String, RoadFacts> roads = new LinkedHashMap<>();
        Map<String, LaneFacts> lanes = new LinkedHashMap<>();

        for (Road road : city.getRoads()) {

            List<LaneFacts> laneFacts = new ArrayList<>();

            for (Lane lane : road.getLanes()) {

                boolean locallyOpen = lane.isEntryOpen();

                boolean entryAllowed =
                        road.isEntryOpen() && locallyOpen;

                LaneFacts fact = new LaneFacts(
                        lane.getId(),
                        road.getId(),
                        locallyOpen,
                        entryAllowed,
                        sortedReasons(lane.getEntryBlockReasons())
                );

                if (lanes.putIfAbsent(fact.id(), fact) != null) {
                    throw new IllegalStateException(
                            "Duplicate lane ID: " + fact.id());
                }

                laneFacts.add(fact);
            }

            boolean entryAvailable = laneFacts.stream()
                    .anyMatch(LaneFacts::entryAllowed);

            RoadFacts roadFact = new RoadFacts(
                    road.getId(),
                    road.isEntryOpen(),
                    entryAvailable,
                    vehicleCounts.getOrDefault(road.getId(), 0),
                    sortedReasons(road.getEntryBlockReasons()),
                    laneFacts
            );

            if (roads.putIfAbsent(roadFact.id(), roadFact) != null) {
                throw new IllegalStateException(
                        "Duplicate road ID: " + roadFact.id());
            }
        }

        // Capture the rule-specific facts at the same tick.
        RuleObservations observations =
                RuleObservations.capture(city);

        return new SimulationContext(
                clock.getElapsedSeconds(),
                clock.getTickCount(),
                weather,
                roads,
                lanes,
                observations
        );
    }

    private static List<String> sortedReasons(Set<String> reasons) {
        List<String> result = new ArrayList<>(reasons);
        Collections.sort(result);
        return List.copyOf(result);
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public long getTickCount() {
        return tickCount;
    }

    public Weather getWeather() {
        return weather;
    }

    public Collection<RoadFacts> getRoads() {
        return roadsById.values();
    }

    public RoadFacts getRoadFacts(String roadId) {
        RoadFacts result = roadsById.get(roadId);

        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown road: " + roadId);
        }

        return result;
    }

    public LaneFacts getLaneFacts(String laneId) {
        LaneFacts result = lanesById.get(laneId);

        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown lane: " + laneId);
        }

        return result;
    }

    public int getVehicleCountOnRoad(String roadId) {
        return getRoadFacts(roadId).vehicleCount();
    }

    public boolean isRoadEntryAvailable(String roadId) {
        return getRoadFacts(roadId).entryAvailable();
    }

    public boolean isLaneEntryAllowed(String laneId) {
        return getLaneFacts(laneId).entryAllowed();
    }

    public RuleObservations getRuleObservations() {
        return ruleObservations;
    }
}
