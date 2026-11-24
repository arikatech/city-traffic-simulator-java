package com.smartcity.simulator.signals;

import com.smartcity.simulator.city.Road;
import java.util.Objects;

public final class TrafficLight implements TrafficSignal {

    private final Road incomingRoad;
    private final IntersectionController controller;

    TrafficLight(Road incomingRoad, IntersectionController controller) {
        this.incomingRoad = Objects.requireNonNull(incomingRoad);
        this.controller = Objects.requireNonNull(controller);
    }

    public Road getIncomingRoad() {
        return incomingRoad;
    }

    @Override
    public SignalState getState() {
        return controller.getStateFor(incomingRoad);
    }

    @Override
    public boolean canProceed() {
        return getState() == SignalState.GREEN;
    }
}