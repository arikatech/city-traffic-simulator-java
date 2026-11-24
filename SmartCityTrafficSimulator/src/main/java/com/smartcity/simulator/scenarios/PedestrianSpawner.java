package com.smartcity.simulator.scenarios;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Crosswalk;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.pedestrians.PedestrianState;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Deterministic, bounded pedestrian arrivals. No Swing dependency.
 * Invoked by SimulationEngine AFTER a committed logical tick.
 * There is at most one active pedestrian per crosswalk to make the
 * Stage 3 reservation and visible crossing state easy to inspect.
 */
public final class PedestrianSpawner {
    private static final double DEFAULT_WALKING_SPEED_MPS = 1.4;
    private final CityMap city;
    private final List<Crosswalk> crosswalks;
    private final double pedestriansPerSecond;
    private final Random random;
    private final int maxAutomaticSpawns;
    private double accumulatedSpawnCredit;
    private int automaticCount;
    private int manualCount;
    private int nextManualCrosswalk;

    public PedestrianSpawner(CityMap city, List<Crosswalk> crosswalks,
            double pedestriansPerSecond, long seed, int maxAutomaticSpawns) {
        this.city = Objects.requireNonNull(city, "city");
        this.crosswalks = List.copyOf(Objects.requireNonNull(crosswalks, "crosswalks"));
        if (this.crosswalks.isEmpty()) {
            throw new IllegalArgumentException("At least one crosswalk is required");
        }
        for (Crosswalk crossing : this.crosswalks) {
            if (!city.getIntersections().contains(crossing.getIntersection())) {
                throw new IllegalArgumentException("Crosswalk outside the city: "
                        + crossing.getId());
            }
        }
        if (!Double.isFinite(pedestriansPerSecond) || pedestriansPerSecond < 0) {
            throw new IllegalArgumentException("Invalid pedestrian spawn rate");
        }
        if (maxAutomaticSpawns < 0) {
            throw new IllegalArgumentException("Automatic spawn cap cannot be negative");
        }
        this.pedestriansPerSecond = pedestriansPerSecond;
        this.random = new Random(seed);
        this.maxAutomaticSpawns = maxAutomaticSpawns;
    }

    /** One fixed dt; a generated pedestrian first acts in the following tick. */
    public void advance(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) {
            throw new IllegalArgumentException("dt must be positive and finite");
        }
        if (automaticCount >= maxAutomaticSpawns) return;
        accumulatedSpawnCredit += pedestriansPerSecond * dt;
        while (accumulatedSpawnCredit + 1e-10 >= 1.0
                && automaticCount < maxAutomaticSpawns) {
            accumulatedSpawnCredit -= 1.0;
            int startIndex = random.nextInt(crosswalks.size());
            Crosswalk free = firstFreeFrom(startIndex);
            if (free != null) {
                automaticCount++;
                city.addPedestrian(new Pedestrian(
                        "AUTO-PED-" + automaticCount,
                        free, DEFAULT_WALKING_SPEED_MPS));
            }
            // If all crossings are busy, skip the due spawn, just like
            // the Stage 4 vehicle spawner skips blocked entries.
        }
    }

    /** Manual spawning is independent of the automatic quota. Null if busy. */
    public Pedestrian spawnManual() {
        Crosswalk free = firstFreeFrom(nextManualCrosswalk);
        if (free == null) return null;
        nextManualCrosswalk = (crosswalks.indexOf(free) + 1) % crosswalks.size();
        Pedestrian pedestrian = new Pedestrian(
                "MANUAL-PED-" + ++manualCount, free,
                DEFAULT_WALKING_SPEED_MPS);
        city.addPedestrian(pedestrian);
        return pedestrian;
    }

    private Crosswalk firstFreeFrom(int startIndex) {
        for (int offset = 0; offset < crosswalks.size(); offset++) {
            Crosswalk crossing = crosswalks.get((startIndex + offset) % crosswalks.size());
            if (!occupied(crossing)) return crossing;
        }
        return null;
    }

    private boolean occupied(Crosswalk crossing) {
        for (MobileEntity entity : city.getMobileEntities()) {
            if (entity instanceof Pedestrian pedestrian
                    && pedestrian.getCrosswalk() == crossing
                    && pedestrian.getState() != PedestrianState.ARRIVED) {
                return true;
            }
        }
        return false;
    }

    public int getAutomaticSpawnCount() { return automaticCount; }
    public int getManualSpawnCount() { return manualCount; }
}
