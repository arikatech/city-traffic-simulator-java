package com.smartcity.simulator.ui.map;

import java.util.Objects;

/** A stable reference to a drawn object, never the mutable domain object. */
public record MapSelection(Kind kind, String id) {
    public enum Kind { VEHICLE, PEDESTRIAN, SIGNAL, ROAD, INTERSECTION, CROSSWALK, BUS_STOP, INCIDENT }

    public MapSelection {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Selection requires an ID");
    }
}
