package com.smartcity.simulator.events;

import com.smartcity.simulator.city.Road;
import java.util.Objects;

/** One-shot event that changes a road's base speed limit from a specified time onward. */
public final class SpeedLimitChangeEvent extends SimulationEvent {

    private final Road road;
    private final double newSpeedLimit;

    public SpeedLimitChangeEvent(String id, double atSeconds,
                                 Road road, double newSpeedLimit) {
        super(id, atSeconds, atSeconds); // One-shot event; no end-of-event cleanup.
        this.road = Objects.requireNonNull(road, "road");
        if (!Double.isFinite(newSpeedLimit) || newSpeedLimit <= 0.0) {
            throw new IllegalArgumentException("Speed limit must be positive and finite");
        }
        this.newSpeedLimit = newSpeedLimit;
    }

    @Override
    protected void onStart(EventContext context) {
        road.setBaseSpeedLimit(newSpeedLimit);
    }
}
