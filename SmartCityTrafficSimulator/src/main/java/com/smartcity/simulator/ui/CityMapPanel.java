package com.smartcity.simulator.ui;

import com.smartcity.simulator.simulation.view.MapSnapshot;
import com.smartcity.simulator.simulation.view.MapSnapshot.*;
import com.smartcity.simulator.ui.map.LaneProjection;
import com.smartcity.simulator.ui.map.MapSelection;
import com.smartcity.simulator.ui.map.MapHitTester;
import java.util.function.Consumer;
import java.awt.*;
import java.awt.geom.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Read-only renderer. The actual engine must publish snapshots separately.
 * paintComponent() never advances logical time or mutates simulation state.
 * All Swing calls (including setSnapshot) occur on the Event Dispatch Thread.
 */
public final class CityMapPanel extends JPanel {

    private static final Color GROUND = new Color(226, 238, 232);
    private static final Color ROAD_OUTLINE = new Color(88, 103, 113);
    private static final Color ROAD_SURFACE = new Color(54, 67, 79);
    private static final Color ROAD_MARKINGS = new Color(223, 218, 169);
    private static final Color INK = new Color(36, 56, 68);

    private MapSnapshot snapshot;
    private MapSelection selection;
    private Set<String> matchingRuleVehicleIds = Set.of();
    private Set<String> speedConstrainedVehicleIds = Set.of();
    private Consumer<MapSelection> selectionListener = picked -> {};

    public CityMapPanel() {
        this(MapSnapshot.empty(1000, 620));
    }

    public CityMapPanel(MapSnapshot initialSnapshot) {
        snapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
        setOpaque(true);
        setBackground(GROUND);
        setPreferredSize(new Dimension(940, 620));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                MapSelection picked = MapHitTester.pick(snapshot,
                        getWidth(), getHeight(), e.getX(), e.getY());
                setSelection(picked);
                selectionListener.accept(picked);
            }
        });
    }

    public void setSnapshot(MapSnapshot newSnapshot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Update Swing map snapshots on the EDT");
        }
        snapshot = Objects.requireNonNull(newSnapshot, "newSnapshot");
        repaint();
    }

    public MapSnapshot getSnapshot() {
        return snapshot;
    }

    /** Selection is a UI reference, never a mutable simulation entity. */
    public void setSelection(MapSelection selected) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Selections must change on EDT");
        selection = selected;
        repaint();
    }

    /** Visual indications of last-tick rules, derived from immutable engine observations. */
    public void setRuleHighlights(Set<String> matching, Set<String> constrained) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Rule overlays must update on EDT");
        matchingRuleVehicleIds = Set.copyOf(Objects.requireNonNull(matching));
        speedConstrainedVehicleIds = Set.copyOf(Objects.requireNonNull(constrained));
        repaint();
    }

    public MapSelection getSelection() { return selection; }

    public void onSelection(Consumer<MapSelection> listener) {
        selectionListener = Objects.requireNonNull(listener);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            MapSnapshot view = snapshot;
            MapHitTester.Viewport viewport =
                    MapHitTester.viewport(view, getWidth(), getHeight());
            double scale = viewport.scale();
            double offsetX = viewport.offsetX();
            double offsetY = viewport.offsetY();

            g.translate(offsetX, offsetY);
            g.scale(scale, scale);

            paintGround(g, view);
            Map<String, RoadView> roadById = new HashMap<>();
            for (RoadView road : view.roads()) {
                roadById.put(road.id(), road);
                paintRoad(g, road);
            }
            for (IntersectionView intersection : view.intersections()) {
                paintIntersection(g, intersection);
            }
            for (CrosswalkView crosswalk : view.crosswalks()) {
                paintCrosswalk(g, crosswalk);
            }
            for (BusStopView stop : view.busStops()) {
                paintBusStop(g, stop);
            }
            for (IncidentView incident : view.incidents()) {
                RoadView road = roadById.get(incident.roadId());
                if (road != null) paintIncident(g, incident, road);
            }
            for (SignalView signal : view.signals()) {
                paintSignal(g, signal);
            }
            for (VehicleView vehicle : view.vehicles()) {
                RoadView road = roadById.get(vehicle.roadId());
                if (road != null && vehicle.laneIndex() < road.laneCount()) {
                    paintVehicle(g, vehicle, road);
                }
            }
            for (PedestrianView pedestrian : view.pedestrians()) {
                paintPedestrian(g, pedestrian);
            }
            paintSelectionHalo(g, view, roadById);
        } finally {
            g.dispose();
        }
    }

    private void paintSelectionHalo(Graphics2D g, MapSnapshot map,
            Map<String, RoadView> roads) {
        if (selection == null) return;
        Point2D target = null;
        switch (selection.kind()) {
            case VEHICLE -> {
                for (VehicleView v : map.vehicles()) {
                    if (!v.id().equals(selection.id())) continue;
                    RoadView road = roads.get(v.roadId());
                    if (road != null && v.laneIndex() < road.laneCount())
                        target = LaneProjection.positionOnLane(road, v.laneIndex(), v.distanceMeters());
                    break;
                }
            }
            case PEDESTRIAN -> {
                for (PedestrianView p : map.pedestrians())
                    if (p.id().equals(selection.id())) target = new Point2D.Double(p.x(), p.y());
            }
            case SIGNAL -> {
                for (SignalView p : map.signals())
                    if (p.id().equals(selection.id())) target = new Point2D.Double(p.x(), p.y());
            }
            case INTERSECTION -> {
                for (IntersectionView p : map.intersections())
                    if (p.id().equals(selection.id())) target = new Point2D.Double(p.x(), p.y());
            }
            case CROSSWALK -> {
                for (CrosswalkView p : map.crosswalks())
                    if (p.id().equals(selection.id())) target = new Point2D.Double(p.x(), p.y());
            }
            case BUS_STOP -> {
                for (BusStopView p : map.busStops())
                    if (p.id().equals(selection.id())) target = new Point2D.Double(p.x(), p.y());
            }
            case INCIDENT -> {
                for (IncidentView p : map.incidents()) {
                    if (!p.id().equals(selection.id())) continue;
                    RoadView road = roads.get(p.roadId());
                    if (road != null) target = new Point2D.Double(
                            (road.x1() + road.x2()) / 2, (road.y1() + road.y2()) / 2);
                }
            }
            case ROAD -> {
                RoadView road = roads.get(selection.id());
                if (road != null) target = new Point2D.Double(
                        (road.x1() + road.x2()) / 2, (road.y1() + road.y2()) / 2);
            }
        }
        if (target == null) return;
        g.setColor(new Color(255, 234, 92, 80));
        g.fill(new Ellipse2D.Double(target.getX() - 20, target.getY() - 20, 40, 40));
        g.setColor(new Color(249, 175, 39));
        g.setStroke(new BasicStroke(2.6f));
        g.draw(new Ellipse2D.Double(target.getX() - 18, target.getY() - 18, 36, 36));
    }

    private void paintGround(Graphics2D g, MapSnapshot view) {
        g.setColor(GROUND);
        g.fill(new Rectangle2D.Double(0, 0, view.mapWidth(), view.mapHeight()));
        g.setStroke(new BasicStroke(0.6f));
        g.setColor(new Color(203, 222, 210));
        for (int x = 25; x < view.mapWidth(); x += 50) {
            g.draw(new Line2D.Double(x, 0, x, view.mapHeight()));
        }
        for (int y = 25; y < view.mapHeight(); y += 50) {
            g.draw(new Line2D.Double(0, y, view.mapWidth(), y));
        }
        // Decorative land parcels, not part of the physical simulator.
        paintPark(g, 120, 95, 155, 83, "PARK");
        paintPark(g, 548, 330, 165, 75, "GREEN SPACE");
        paintBuilding(g, 535, 98, 105, 70, "LIBRARY");
        paintBuilding(g, 170, 335, 120, 80, "SCHOOL");
        paintBuilding(g, 860, 345, 90, 65, "HOSPITAL");
    }

    private void paintPark(Graphics2D g, double x, double y, int w, int h, String title) {
        g.setColor(new Color(179, 215, 182));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 15, 15));
        g.setColor(new Color(117, 167, 118));
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 15, 15));
        for (int i = 0; i < 4; i++) {
            g.fill(new Ellipse2D.Double(x + 14 + i * 34, y + 12, 13, 13));
        }
        label(g, title, x + w / 2.0, y + h - 19, 11, INK, true);
    }

    private void paintBuilding(Graphics2D g, double x, double y, int w, int h, String title) {
        g.setColor(new Color(245, 239, 221));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 10, 10));
        g.setColor(new Color(182, 168, 142));
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 10, 10));
        label(g, title, x + w / 2.0, y + h / 2.0 + 4, 11, INK, true);
    }

    private void paintRoad(Graphics2D g, RoadView road) {
        double width = road.laneCount() * LaneProjection.LANE_SPACING + 10;
        Line2D.Double axis = new Line2D.Double(road.x1(), road.y1(), road.x2(), road.y2());
        g.setStroke(new BasicStroke((float) (width + 5), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(ROAD_OUTLINE);
        g.draw(axis);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(ROAD_SURFACE);
        g.draw(axis);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 10f, new float[]{12f, 10f}, 0));
        g.setColor(ROAD_MARKINGS);
        double dx = road.x2() - road.x1();
        double dy = road.y2() - road.y1();
        double nX = -dy / Math.hypot(dx, dy);
        double nY = dx / Math.hypot(dx, dy);
        for (int i = 1; i < road.laneCount(); i++) {
            double off = (i - road.laneCount() / 2.0) * LaneProjection.LANE_SPACING;
            g.draw(new Line2D.Double(road.x1() + nX * off, road.y1() + nY * off,
                    road.x2() + nX * off, road.y2() + nY * off));
        }
        if (road.closed()) {
            g.setColor(new Color(232, 103, 69, 150));
            g.setStroke(new BasicStroke((float) (width + 1), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(axis);
        }
        double fraction = road.closed() ? 0.82 : 0.23;
        double mx = road.x1() + fraction * dx;
        double my = road.y1() + fraction * dy;
        pillLabel(g, road.id() + " · " + (int) road.lengthMeters() + " m",
                mx - nX * (width / 2 + 20), my - nY * (width / 2 + 20),
                new Color(238, 246, 242), INK);
    }

    private void paintIntersection(Graphics2D g, IntersectionView junction) {
        g.setColor(ROAD_OUTLINE);
        g.fill(new RoundRectangle2D.Double(junction.x() - 24, junction.y() - 24,
                48, 48, 6, 6));
        g.setColor(ROAD_SURFACE);
        g.fill(new RoundRectangle2D.Double(junction.x() - 21, junction.y() - 21,
                42, 42, 5, 5));
        label(g, junction.id(), junction.x(), junction.y() - 31, 10, INK, true);
    }

    private void paintCrosswalk(Graphics2D g, CrosswalkView crossing) {
        g.setColor(new Color(245, 246, 237));
        for (int i = -3; i <= 3; i++) {
            double x = crossing.x() + (crossing.horizontal() ? i * 7 : -19);
            double y = crossing.y() + (crossing.horizontal() ? -19 : i * 7);
            g.fill(new Rectangle2D.Double(x, y,
                    crossing.horizontal() ? 4 : 38,
                    crossing.horizontal() ? 38 : 4));
        }
    }

    private void paintBusStop(Graphics2D g, BusStopView stop) {
        g.setColor(new Color(49, 117, 170));
        g.fill(new RoundRectangle2D.Double(stop.x() - 11, stop.y() - 11, 22, 22, 5, 5));
        label(g, "B", stop.x(), stop.y() + 5, 15, Color.WHITE, true);
        label(g, stop.id(), stop.x(), stop.y() - 18, 10, INK, true);
    }

    private void paintSignal(Graphics2D g, SignalView signal) {
        g.setColor(new Color(28, 38, 48));
        g.fill(new RoundRectangle2D.Double(signal.x() - 8, signal.y() - 18, 16, 36, 5, 5));
        for (int i = 0; i < 3; i++) {
            SignalColor state = SignalColor.values()[i];
            Color bright = switch (state) {
                case RED -> new Color(252, 100, 94);
                case YELLOW -> new Color(247, 194, 72);
                case GREEN -> new Color(90, 219, 144);
            };
            g.setColor(signal.color() == state ? bright : new Color(72, 80, 85));
            g.fill(new Ellipse2D.Double(signal.x() - 4.5, signal.y() - 15 + i * 12,
                    9, 9));
        }
    }

    private void paintIncident(Graphics2D g, IncidentView incident, RoadView road) {
        double x = (road.x1() + road.x2()) / 2.0;
        double y = (road.y1() + road.y2()) / 2.0;
        g.setColor(new Color(255, 185, 65));
        Path2D.Double triangle = new Path2D.Double();
        triangle.moveTo(x, y - 29);
        triangle.lineTo(x - 17, y - 1);
        triangle.lineTo(x + 17, y - 1);
        triangle.closePath();
        g.fill(triangle);
        g.setColor(new Color(125, 67, 26));
        g.setStroke(new BasicStroke(2));
        g.draw(triangle);
        label(g, "!", x, y - 9, 19, new Color(76, 45, 22), true);
        String title = incident.kind() == IncidentKind.ACCIDENT ? "ACCIDENT" : "ROADWORK";
        pillLabel(g, title, x, y + 26, new Color(255, 240, 215), new Color(142, 64, 37));
    }

    private void paintVehicle(Graphics2D g, VehicleView vehicle, RoadView road) {
        Point2D.Double point = LaneProjection.positionOnLane(
                road, vehicle.laneIndex(), vehicle.distanceMeters());
        Graphics2D v = (Graphics2D) g.create();
        try {
            v.translate(point.x, point.y);
            v.rotate(LaneProjection.directionRadians(road));
            double length = switch (vehicle.kind()) {
                case BUS -> 32;
                case TRUCK, FIRE_TRUCK -> 28;
                default -> 21;
            };
            Color fill = switch (vehicle.kind()) {
                case CAR -> new Color(67, 172, 224);
                case BUS -> new Color(247, 191, 66);
                case TRUCK -> new Color(158, 169, 177);
                case AMBULANCE -> new Color(242, 249, 248);
                case POLICE_CAR -> new Color(68, 113, 221);
                case FIRE_TRUCK -> new Color(215, 74, 66);
            };
            v.setColor(new Color(22, 32, 44, 120));
            v.fill(new RoundRectangle2D.Double(-length / 2 + 2, -6, length, 12, 5, 5));
            v.setColor(fill);
            v.fill(new RoundRectangle2D.Double(-length / 2, -7, length, 13, 4, 4));
            v.setColor(new Color(38, 61, 77));
            v.fill(new RoundRectangle2D.Double(length * 0.1, -5.5, length * 0.2, 11, 2, 2));
            if (vehicle.kind() == VehicleKind.AMBULANCE
                    || vehicle.kind() == VehicleKind.POLICE_CAR
                    || vehicle.kind() == VehicleKind.FIRE_TRUCK) {
                v.setColor(new Color(231, 77, 79));
                v.fill(new Rectangle2D.Double(-3, -4, 6, 8));
            }
            v.setColor(new Color(32, 44, 56));
            v.setStroke(new BasicStroke(1.2f));
            v.draw(new RoundRectangle2D.Double(-length / 2, -7, length, 13, 4, 4));
        } finally {
            v.dispose();
        }
        // Purple = matched at least one rule; orange = speed/stop cap reduced.
        // Read-only decoration: never alters the vehicle's logical speed.
        boolean capped = speedConstrainedVehicleIds.contains(vehicle.id());
        boolean matched = matchingRuleVehicleIds.contains(vehicle.id());
        if (capped || matched) {
            g.setColor(capped ? new Color(249, 156, 42) : new Color(164, 104, 218));
            g.setStroke(new BasicStroke(2.4f));
            g.draw(new Ellipse2D.Double(point.x - 17, point.y - 17, 34, 34));
        }
        pillLabel(g, vehicle.id(), point.x, point.y - 18, Color.WHITE, INK);
    }

    private void paintPedestrian(Graphics2D g, PedestrianView pedestrian) {
        Color fill = switch (pedestrian.state()) {
            case WAITING -> new Color(224, 133, 63);
            case CROSSING -> new Color(65, 153, 109);
            case ARRIVED -> new Color(107, 129, 135);
        };
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(pedestrian.x() - 8, pedestrian.y() - 8, 16, 16));
        g.setColor(fill);
        g.fill(new Ellipse2D.Double(pedestrian.x() - 6, pedestrian.y() - 6, 12, 12));
        label(g, pedestrian.id(), pedestrian.x(), pedestrian.y() - 12, 9, INK, true);
    }

    private static void pillLabel(Graphics2D g, String text,
            double centerX, double baselineY, Color background, Color foreground) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        int w = g.getFontMetrics().stringWidth(text);
        double x = centerX - w / 2.0;
        g.setColor(background);
        g.fill(new RoundRectangle2D.Double(x - 5, baselineY - 11,
                w + 10, 16, 7, 7));
        g.setColor(foreground);
        g.drawString(text, (float) x, (float) baselineY);
    }

    private static void label(Graphics2D g, String text,
            double centerX, double baselineY, int size, Color color, boolean bold) {
        g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        g.setColor(color);
        g.drawString(text, (float) (centerX - g.getFontMetrics().stringWidth(text) / 2.0),
                (float) baselineY);
    }
}