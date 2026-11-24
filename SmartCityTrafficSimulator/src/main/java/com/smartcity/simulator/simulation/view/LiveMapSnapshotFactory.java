package com.smartcity.simulator.simulation.view;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.vehicles.Vehicle;
import com.smartcity.simulator.simulation.view.MapSnapshot.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure observation adapter. It never calls engine.step(), planStep() or commitStep().
 * Only returns new immutable MapSnapshot values for the Java2D panel.
 * Roads are matched to fixed VISUAL geometry by ID; no physical physics is inferred.
 */
public final class LiveMapSnapshotFactory {
    private final Map<String, RoadView> geometry;
    private final List<IntersectionView> intersections;
    private final List<CrosswalkView> crosswalks;
    private final List<BusStopView> busStops;

    public LiveMapSnapshotFactory(List<RoadView> geometry,
            List<IntersectionView> intersections,
            List<CrosswalkView> crosswalks, List<BusStopView> busStops) {
        this.geometry = new HashMap<>();
        for (RoadView shape : List.copyOf(geometry)) {
            if (this.geometry.putIfAbsent(shape.id(), shape) != null) {
                throw new IllegalArgumentException("Duplicate drawn road: " + shape.id());
            }
        }
        this.intersections = List.copyOf(intersections);
        this.crosswalks = List.copyOf(crosswalks);
        this.busStops = List.copyOf(busStops);
    }

    public MapSnapshot capture(CityMap city, Map<String, SignalColor> signalColors,
            List<IncidentView> knownIncidents) {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(signalColors, "signalColors");
        Objects.requireNonNull(knownIncidents, "knownIncidents");

        List<RoadView> roads = new ArrayList<>();
        for (Road road : city.getRoads()) {
            RoadView shape = geometry.get(road.getId());
            if (shape == null) continue; // a scenario-specific road may be hidden until mapped
            roads.add(new RoadView(shape.id(), shape.x1(), shape.y1(), shape.x2(),
                    shape.y2(), road.getLengthMeters(), road.getLanes().size(),
                    !road.isEntryOpen()));
        }
        List<VehicleView> vehicles = new ArrayList<>();
        for (MobileEntity entity : city.getMobileEntities()) {
            if (!(entity instanceof Vehicle vehicle)) continue;
            Road road = vehicle.getCurrentRoad();
            if (road == null || !geometry.containsKey(road.getId())) continue;
            int index = road.getLanes().indexOf(vehicle.getCurrentLane());
            if (index < 0) continue;
            VehicleKind kind = vehicleKind(vehicle.getClass().getSimpleName());
            vehicles.add(new VehicleView(vehicle.getId(), kind, road.getId(), index,
                    Math.max(0, vehicle.getPositionMeters())));
        }
        List<SignalView> signals = new ArrayList<>();
        // Locations identify the incoming-road signals in the demonstration city.
        addSignal(signals, signalColors, "R-1", "TL-1", 405, 226);
        addSignal(signals, signalColors, "R-2", "TL-2", 785, 226);
        addSignal(signals, signalColors, "R-6", "TL-3", 461, 445);
        addSignal(signals, signalColors, "R-4", "TL-4", 844, 448);

        // Pedestrian icons only appear when REAL domain pedestrians exist.
        List<PedestrianView> pedestrians = new ArrayList<>();
        for (MobileEntity entity : city.getMobileEntities()) {
            if (!(entity instanceof Pedestrian p)) continue;
            CrosswalkView cross = crosswalks.stream()
                    .filter(c -> c.id().equals(p.getCrosswalk().getId()))
                    .findFirst().orElse(null);
            if (cross == null) continue;
            double progress = Math.max(0.0, Math.min(1.0,
                    p.getPositionMeters() / p.getCrosswalk().getLengthMeters()));
            // CrosswalkView.horizontal() describes how the zebra stripes are
            // ARRANGED, not the pedestrian's walking direction. Pedestrians
            // walk perpendicular to those stripes, from sidewalk to sidewalk.
            // A 64-unit path starts/ends beyond the road edge, including
            // the wider two-lane road at CW-1. Only the display is changed.
            double travel = (progress - 0.5) * 64.0;
            double x = cross.x() + (cross.horizontal() ? 0 : travel);
            double y = cross.y() + (cross.horizontal() ? travel : 0);
            MapSnapshot.PedestrianState state = switch (p.getState()) {
                case WAITING -> MapSnapshot.PedestrianState.WAITING;
                case CROSSING -> MapSnapshot.PedestrianState.CROSSING;
                case ARRIVED -> MapSnapshot.PedestrianState.ARRIVED;
            };
            pedestrians.add(new PedestrianView(p.getId(), x, y, state));
        }

        return new MapSnapshot(1000, 620, roads, intersections, crosswalks,
                busStops, signals, vehicles, pedestrians, knownIncidents);
    }

    /** This Stage 7 demo map identifies signals by their incoming road. */
    public static String incomingRoadIdForSignal(String signalId) {
        return switch (signalId) {
            case "TL-1" -> "R-1";
            case "TL-2" -> "R-2";
            case "TL-3" -> "R-6";
            case "TL-4" -> "R-4";
            default -> null;
        };
    }

    private static void addSignal(List<SignalView> list,
            Map<String, SignalColor> states, String roadId, String id,
            double x, double y) {
        SignalColor color = states.get(roadId);
        if (color != null) list.add(new SignalView(id, x, y, color));
    }

    private static VehicleKind vehicleKind(String name) {
        return switch (name) {
            case "Bus" -> VehicleKind.BUS;
            case "Truck" -> VehicleKind.TRUCK;
            case "Ambulance" -> VehicleKind.AMBULANCE;
            case "PoliceCar" -> VehicleKind.POLICE_CAR;
            case "FireTruck" -> VehicleKind.FIRE_TRUCK;
            default -> VehicleKind.CAR;
        };
    }

    public static LiveMapSnapshotFactory compactCityGeometry() {
        return new LiveMapSnapshotFactory(
            List.of(
                new RoadView("R-1", 70, 260, 435, 260, 250, 2, false),
                new RoadView("R-2", 435, 260, 820, 260, 300, 2, false),
                new RoadView("R-3", 435, 60, 435, 260, 310, 1, false),
                new RoadView("R-4", 820, 260, 820, 485, 190, 1, false),
                new RoadView("R-5", 820, 485, 115, 485, 540, 2, false),
                new RoadView("R-6", 435, 260, 435, 485, 225, 1, false),
                new RoadView("R-7", 435, 485, 820, 485, 300, 1, false)),
            List.of(new IntersectionView("J-1",435,260),
                    new IntersectionView("J-2",820,260),
                    new IntersectionView("J-3",820,485),
                    new IntersectionView("J-4",435,485)),
            List.of(new CrosswalkView("CW-1", 394,260,true),
                    new CrosswalkView("CW-2",820,304,false),
                    new CrosswalkView("CW-3",435,446,false)),
            List.of(new BusStopView("BUS-1",255,232)));
    }
}