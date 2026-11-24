package com.smartcity.simulator.ui.map;

import com.smartcity.simulator.simulation.view.MapSnapshot;
import com.smartcity.simulator.simulation.view.MapSnapshot.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Pixel-based hit testing using EXACTLY the same viewport transform as the renderer. */
public final class MapHitTester {
    public static final double MARGIN = 28.0;
    private static final double ENTITY_RADIUS_PX = 19.0;
    private static final double SIGNAL_RADIUS_PX = 17.0;
    private static final double ROAD_RADIUS_PX = 10.0;

    private MapHitTester() { }

    public record Viewport(double scale, double offsetX, double offsetY) {
        public Point2D.Double screen(double x, double y) {
            return new Point2D.Double(offsetX + scale * x, offsetY + scale * y);
        }
    }

    public static Viewport viewport(MapSnapshot map, int width, int height) {
        Objects.requireNonNull(map);
        double scale = Math.min(
                Math.max(1.0, width - MARGIN * 2) / map.mapWidth(),
                Math.max(1.0, height - MARGIN * 2) / map.mapHeight());
        return new Viewport(scale, (width - scale * map.mapWidth()) / 2.0,
                (height - scale * map.mapHeight()) / 2.0);
    }

    /** Null when the click falls outside all inspectable objects. Nearest visual entity wins. */
    public static MapSelection pick(MapSnapshot map, int width, int height,
            double clickX, double clickY) {
        Objects.requireNonNull(map);
        if (!Double.isFinite(clickX) || !Double.isFinite(clickY)) return null;
        Viewport view = viewport(map, width, height);
        Map<String, RoadView> roads = new HashMap<>();
        for (RoadView road : map.roads()) roads.put(road.id(), road);
        MapSelection best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        // Mobile objects first. Road labels must never steal a car click.
        for (VehicleView vehicle : map.vehicles()) {
            RoadView road = roads.get(vehicle.roadId());
            if (road == null || vehicle.laneIndex() >= road.laneCount()) continue;
            Point2D p = LaneProjection.positionOnLane(road, vehicle.laneIndex(), vehicle.distanceMeters());
            double distance = distance(view, p.getX(), p.getY(), clickX, clickY);
            if (distance <= ENTITY_RADIUS_PX && distance < bestDistance) {
                best = new MapSelection(MapSelection.Kind.VEHICLE, vehicle.id());
                bestDistance = distance;
            }
        }
        for (PedestrianView pedestrian : map.pedestrians()) {
            double distance = distance(view, pedestrian.x(), pedestrian.y(), clickX, clickY);
            if (distance <= ENTITY_RADIUS_PX && distance < bestDistance) {
                best = new MapSelection(MapSelection.Kind.PEDESTRIAN, pedestrian.id());
                bestDistance = distance;
            }
        }
        if (best != null) return best;
        for (SignalView signal : map.signals()) {
            double distance = distance(view, signal.x(), signal.y(), clickX, clickY);
            if (distance <= SIGNAL_RADIUS_PX && distance < bestDistance) {
                best = new MapSelection(MapSelection.Kind.SIGNAL, signal.id());
                bestDistance = distance;
            }
        }
        if (best != null) return best;

        // Fixed map infrastructure, in priority order.
        for (IncidentView incident : map.incidents()) {
            RoadView road = roads.get(incident.roadId());
            if (road == null) continue;
            double x = (road.x1() + road.x2()) / 2.0;
            double y = (road.y1() + road.y2()) / 2.0 - 12;
            if (distance(view, x, y, clickX, clickY) <= 19) {
                return new MapSelection(MapSelection.Kind.INCIDENT, incident.id());
            }
        }
        for (CrosswalkView crosswalk : map.crosswalks()) {
            if (distance(view, crosswalk.x(), crosswalk.y(), clickX, clickY) <= 14) {
                return new MapSelection(MapSelection.Kind.CROSSWALK, crosswalk.id());
            }
        }
        for (BusStopView stop : map.busStops()) {
            if (distance(view, stop.x(), stop.y(), clickX, clickY) <= 15) {
                return new MapSelection(MapSelection.Kind.BUS_STOP, stop.id());
            }
        }
        for (IntersectionView intersection : map.intersections()) {
            if (distance(view, intersection.x(), intersection.y(), clickX, clickY) <= 16) {
                return new MapSelection(MapSelection.Kind.INTERSECTION, intersection.id());
            }
        }
        double minRoadDistance = ROAD_RADIUS_PX;
        for (RoadView road : map.roads()) {
            Point2D a = view.screen(road.x1(), road.y1());
            Point2D b = view.screen(road.x2(), road.y2());
            double distance = segmentDistance(clickX, clickY,
                    a.getX(), a.getY(), b.getX(), b.getY());
            if (distance < minRoadDistance) {
                minRoadDistance = distance;
                best = new MapSelection(MapSelection.Kind.ROAD, road.id());
            }
        }
        return best;
    }

    private static double distance(Viewport view, double x, double y, double clickX, double clickY) {
        Point2D p = view.screen(x, y);
        return Math.hypot(clickX - p.getX(), clickY - p.getY());
    }

    private static double segmentDistance(double px, double py,
            double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double denominator = dx * dx + dy * dy;
        double t = denominator == 0 ? 0 : Math.max(0, Math.min(1,
                ((px - ax) * dx + (py - ay) * dy) / denominator));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
}
