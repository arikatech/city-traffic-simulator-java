package com.smartcity.simulator.city;

import com.smartcity.simulator.signals.IntersectionController;
import java.util.List;

public final class Intersection {

    private final String id;
    private final IntersectionController controller;

    public Intersection(
            String id,
            List<Road> incomingRoads,
            double greenSeconds,
            double yellowSeconds,
            double clearanceSeconds) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Intersection ID cannot be empty."
            );
        }

        this.id = id;

        this.controller = new IntersectionController(
                incomingRoads,
                greenSeconds,
                yellowSeconds,
                clearanceSeconds
        );
    }

    public String getId() {
        return id;
    }

    public List<Road> getIncomingRoads() {
        return controller.getIncomingRoads();
    }

    public IntersectionController getController() {
        return controller;
    }
}