package com.smartcity.simulator.signals;

import java.util.Objects;

public final class PedestrianSignal implements TrafficSignal {

    private final IntersectionController controller;

    public PedestrianSignal(IntersectionController controller) {
        this.controller = Objects.requireNonNull(controller);
    }

    @Override
    public SignalState getState() {
        return canProceed() ? SignalState.GREEN : SignalState.RED;
    }

    @Override
    public boolean canProceed() {
        return controller.canStartPedestrianCrossing();
    }
}