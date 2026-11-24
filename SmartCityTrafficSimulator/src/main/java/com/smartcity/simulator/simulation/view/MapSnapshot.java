package com.smartcity.simulator.simulation.view;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, presentation-oriented facts about the city at ONE instant.
 * Contains no mutable domain entities and does not depend on Swing.
 *
 * Geometry coordinates use arbitrary 2D MAP units. Road lengths and vehicle
 * positions use physical METRES. These are intentionally different systems.
 */
public record MapSnapshot(
        double mapWidth,
        double mapHeight,
        List<RoadView> roads,
        List<IntersectionView> intersections,
        List<CrosswalkView> crosswalks,
        List<BusStopView> busStops,
        List<SignalView> signals,
        List<VehicleView> vehicles,
        List<PedestrianView> pedestrians,
        List<IncidentView> incidents) {

    public MapSnapshot {
        positiveFinite(mapWidth, "mapWidth");
        positiveFinite(mapHeight, "mapHeight");
        roads = List.copyOf(Objects.requireNonNull(roads, "roads"));
        intersections = List.copyOf(Objects.requireNonNull(intersections, "intersections"));
        crosswalks = List.copyOf(Objects.requireNonNull(crosswalks, "crosswalks"));
        busStops = List.copyOf(Objects.requireNonNull(busStops, "busStops"));
        signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
        vehicles = List.copyOf(Objects.requireNonNull(vehicles, "vehicles"));
        pedestrians = List.copyOf(Objects.requireNonNull(pedestrians, "pedestrians"));
        incidents = List.copyOf(Objects.requireNonNull(incidents, "incidents"));
    }

    public static MapSnapshot empty(double width, double height) {
        return new MapSnapshot(width, height,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    public record RoadView(String id, double x1, double y1, double x2,
            double y2, double lengthMeters, int laneCount, boolean closed) {
        public RoadView {
            requiredId(id);
            finite(x1, "x1"); finite(y1, "y1");
            finite(x2, "x2"); finite(y2, "y2");
            positiveFinite(lengthMeters, "lengthMeters");
            if (Math.hypot(x2 - x1, y2 - y1) < 0.001) {
                throw new IllegalArgumentException("Road geometry has no direction: " + id);
            }
            if (laneCount < 1 || laneCount > 4) {
                throw new IllegalArgumentException("Lane count must be 1..4: " + id);
            }
        }
    }

    public record IntersectionView(String id, double x, double y) {
        public IntersectionView {
            requiredId(id); finite(x, "x"); finite(y, "y");
        }
    }

    /** horizontal=true means the zebra stripes extend left/right. */
    public record CrosswalkView(String id, double x, double y, boolean horizontal) {
        public CrosswalkView {
            requiredId(id); finite(x, "x"); finite(y, "y");
        }
    }

    public record BusStopView(String id, double x, double y) {
        public BusStopView {
            requiredId(id); finite(x, "x"); finite(y, "y");
        }
    }

    public enum SignalColor { RED, YELLOW, GREEN }

    public record SignalView(String id, double x, double y, SignalColor color) {
        public SignalView {
            requiredId(id); finite(x, "x"); finite(y, "y");
            Objects.requireNonNull(color, "color");
        }
    }

    public enum VehicleKind {
        CAR, BUS, TRUCK, AMBULANCE, POLICE_CAR, FIRE_TRUCK
    }

    public record VehicleView(String id, VehicleKind kind, String roadId,
            int laneIndex, double distanceMeters) {
        public VehicleView {
            requiredId(id); requiredId(roadId);
            Objects.requireNonNull(kind, "kind");
            if (laneIndex < 0) {
                throw new IllegalArgumentException("Negative lane index");
            }
            if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
                throw new IllegalArgumentException("Invalid vehicle distance");
            }
        }
    }

    public enum PedestrianState { WAITING, CROSSING, ARRIVED }

    public record PedestrianView(String id, double x, double y, PedestrianState state) {
        public PedestrianView {
            requiredId(id); finite(x, "x"); finite(y, "y");
            Objects.requireNonNull(state, "state");
        }
    }

    public enum IncidentKind { ACCIDENT, ROADWORK }

    public record IncidentView(String id, IncidentKind kind, String roadId) {
        public IncidentView {
            requiredId(id); requiredId(roadId);
            Objects.requireNonNull(kind, "kind");
        }
    }

    private static void requiredId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("An identifier must not be blank");
        }
    }

    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void positiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
