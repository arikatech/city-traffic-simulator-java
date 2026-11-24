package com.smartcity.simulator.simulation.view;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.entities.MobileEntity;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.simulation.SimulationEngine;
import com.smartcity.simulator.simulation.SimulationEngine.VehicleRuleObservation;
import com.smartcity.simulator.simulation.view.InspectionSnapshot.Field;
import com.smartcity.simulator.simulation.view.MapSnapshot.*;
import com.smartcity.simulator.ui.map.MapSelection;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-only domain-to-inspector adapter; no Swing or engine side effects. */
public final class InspectionSnapshotFactory {
    private InspectionSnapshotFactory() { }

    /** Return null only when the selected object is no longer present. */
    public static InspectionSnapshot capture(CityMap city, SimulationEngine engine,
            MapSnapshot map, MapSelection selection) {
        Objects.requireNonNull(city); Objects.requireNonNull(engine);
        Objects.requireNonNull(map); Objects.requireNonNull(selection);
        return switch (selection.kind()) {
            case VEHICLE -> vehicle(city, engine, selection.id());
            case PEDESTRIAN -> pedestrian(city, selection.id());
            case SIGNAL -> signal(city, map, selection.id());
            case ROAD -> road(city, selection.id());
            case INTERSECTION -> intersection(city, selection.id());
            case CROSSWALK -> map.crosswalks().stream().filter(v -> v.id().equals(selection.id()))
                    .findFirst().map(v -> new InspectionSnapshot("Crosswalk " + v.id(),
                        "Pedestrian crossing on the map", List.of(
                            new Field("Orientation", v.horizontal() ? "Horizontal stripes" : "Vertical stripes"))))
                    .orElse(null);
            case BUS_STOP -> map.busStops().stream().filter(v -> v.id().equals(selection.id()))
                    .findFirst().map(v -> new InspectionSnapshot("Bus stop " + v.id(),
                        "Visual bus-stop landmark", List.of())).orElse(null);
            case INCIDENT -> map.incidents().stream().filter(v -> v.id().equals(selection.id()))
                    .findFirst().map(v -> new InspectionSnapshot(v.kind() + " " + v.id(),
                        "Active incident marker", List.of(new Field("Affected road", v.roadId()))))
                    .orElse(null);
        };
    }

    private static InspectionSnapshot vehicle(CityMap city, SimulationEngine engine, String id) {
        for (MobileEntity entity : city.getMobileEntities()) {
            if (!(entity instanceof Vehicle vehicle) || !vehicle.getId().equals(id)) continue;
            List<Field> fields = new ArrayList<>();
            fields.add(new Field("ID", vehicle.getId()));
            fields.add(new Field("Concrete type", vehicle.getClass().getSimpleName()));
            fields.add(new Field("Current road", vehicle.getCurrentRoad().getId()));
            fields.add(new Field("Current lane", vehicle.getCurrentLane().getId()));
            fields.add(new Field("Position", metres(vehicle.getPositionMeters())));
            Double measuredSpeed = engine.getLastCommittedVehicleSpeeds()
                    .get(vehicle.getId());
            fields.add(new Field("Actual speed (last tick)", measuredSpeed == null
                    ? "Not measured yet" : speed(measuredSpeed)));
            fields.add(new Field("Requested cruise speed",
                    speed(vehicle.getSpeedMetersPerSecond())));
            fields.add(new Field("Signal priority request",
                    vehicle.requestsSignalPriority() ? "Active (safe request)" : "No"));
            fields.add(new Field("Physical maximum", speed(vehicle.getPhysicalMaximumSpeed())));
            if (vehicle.hasRoute()) {
                List<String> ids = vehicle.getRoute().getRoads().stream().map(Road::getId).toList();
                fields.add(new Field("Planned route", String.join(" → ", ids)));
                fields.add(new Field("Next road", vehicle.hasNextRoad() ?
                        vehicle.getNextRoad().getId() : "Final destination road"));
            } else {
                fields.add(new Field("Planned route", "Not assigned"));
            }
            VehicleRuleObservation decision = engine.getLastVehicleRuleObservations().get(id);
            if (decision == null) {
                double baseline = Math.min(vehicle.getPhysicalMaximumSpeed(),
                        vehicle.getCurrentRoad().getBaseSpeedLimit());
                fields.add(new Field("Ordinary baseline cap", speed(baseline)));
                fields.add(new Field("Resolved permitted cap", "Not evaluated yet"));
                fields.add(new Field("Matched rule IDs", "Not evaluated yet"));
            } else {
                fields.add(new Field("Last rule evaluation", seconds(decision.evaluatedAtSeconds())));
                fields.add(new Field("Ordinary baseline cap", speed(decision.baselineCapMetersPerSecond())));
                fields.add(new Field("Resolved permitted cap", speed(decision.permittedCapMetersPerSecond())));
                fields.add(new Field("Matched rule IDs", decision.matchedRuleIds().isEmpty()
                        ? "None" : String.join(", ", decision.matchedRuleIds())));
            }
            return new InspectionSnapshot("Vehicle " + id,
                    "Live committed state; rule caps from the last pre-movement tick", fields);
        }
        return null;
    }

    private static InspectionSnapshot pedestrian(CityMap city, String id) {
        for (MobileEntity entity : city.getMobileEntities()) {
            if (!(entity instanceof Pedestrian p) || !p.getId().equals(id)) continue;
            return new InspectionSnapshot("Pedestrian " + id, "Live committed pedestrian state", List.of(
                    new Field("ID", p.getId()),
                    new Field("State", p.getState().toString()),
                    new Field("Crosswalk", p.getCrosswalk().getId()),
                    new Field("Distance", metres(p.getPositionMeters())),
                    new Field("Waiting time", seconds(p.getWaitingSeconds()))));
        }
        return null;
    }

    private static InspectionSnapshot signal(CityMap city, MapSnapshot map, String id) {
        SignalView visible = map.signals().stream().filter(v -> v.id().equals(id)).findFirst().orElse(null);
        if (visible == null) return null;
        String roadId = LiveMapSnapshotFactory.incomingRoadIdForSignal(id);
        if (roadId == null) {
            return new InspectionSnapshot("Signal " + id, "Visible signal", List.of(
                    new Field("Current light", visible.color().toString())));
        }
        for (Intersection junction : city.getIntersections()) {
            for (Road road : junction.getIncomingRoads()) {
                if (road.getId().equals(roadId)) {
                    return new InspectionSnapshot("Traffic signal " + id,
                            "Controller-managed traffic light", List.of(
                            new Field("Incoming road", road.getId()),
                            new Field("Intersection", junction.getId()),
                            new Field("Current light", junction.getController().getStateFor(road).toString()),
                            new Field("Can proceed", String.valueOf(
                                    junction.getController().getSignalFor(road).canProceed())),
                            new Field("Conflict zone reserved", String.valueOf(junction.getController().isReserved()))));
                }
            }
        }
        return new InspectionSnapshot("Signal " + id, "Visual signal only", List.of(
                new Field("Current light", visible.color().toString())));
    }

    private static InspectionSnapshot road(CityMap city, String id) {
        for (Road r : city.getRoads()) {
            if (!r.getId().equals(id)) continue;
            long count = city.getMobileEntities().stream()
                    .filter(e -> e instanceof Vehicle v && v.getCurrentRoad() == r).count();
            return new InspectionSnapshot("Road " + id, "Live road state", List.of(
                    new Field("ID", r.getId()), new Field("Length", metres(r.getLengthMeters())),
                    new Field("Speed limit", speed(r.getBaseSpeedLimit())),
                    new Field("Lanes", Integer.toString(r.getLanes().size())),
                    new Field("Entry permitted", r.isEntryOpen() ? "Yes" : "No"),
                    new Field("Vehicles on road", Long.toString(count)),
                    new Field("Outgoing roads", r.getOutgoingRoads().isEmpty()
                            ? "Destination / dead end" : String.join(", ",
                                r.getOutgoingRoads().stream().map(Road::getId).toList()))));
        }
        return null;
    }

    private static InspectionSnapshot intersection(CityMap city, String id) {
        for (Intersection j : city.getIntersections()) {
            if (!j.getId().equals(id)) continue;
            return new InspectionSnapshot("Intersection " + id, "Controlled conflict zone", List.of(
                    new Field("Incoming roads", String.join(", ",
                            j.getIncomingRoads().stream().map(Road::getId).toList())),
                    new Field("Reservation active", String.valueOf(j.getController().isReserved())),
                    new Field("Reserved vehicle", j.getController().getReservedVehicleId() == null
                            ? "None" : j.getController().getReservedVehicleId())));
        }
        return null;
    }

    private static String speed(double x) { return String.format(Locale.ROOT, "%.2f m/s", x); }
    private static String metres(double x) { return String.format(Locale.ROOT, "%.1f m", x); }
    private static String seconds(double x) { return String.format(Locale.ROOT, "%.1f s", x); }
}