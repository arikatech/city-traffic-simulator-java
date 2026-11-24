package com.smartcity.simulator.ui.map;

import com.smartcity.simulator.simulation.view.MapSnapshot.RoadView;
import java.awt.geom.Point2D;
import java.util.Objects;

/** Pure geometry: never reads or changes an engine, Road, Lane or Vehicle. */
public final class LaneProjection {

    /** Visual distance between adjacent lanes, measured in map units. */
    public static final double LANE_SPACING = 15.0;

    private LaneProjection() { }

    /**
     * Convert metres along the physical road into a 2D map position.
     * Lanes are indexed 0..laneCount-1, and offset around the centre line.
     */
    public static Point2D.Double positionOnLane(
            RoadView road, int laneIndex, double distanceMeters) {
        Objects.requireNonNull(road, "road");
        if (laneIndex < 0 || laneIndex >= road.laneCount()) {
            throw new IllegalArgumentException("Lane index outside road: " + laneIndex);
        }
        if (!Double.isFinite(distanceMeters)) {
            throw new IllegalArgumentException("Distance must be finite");
        }

        double dx = road.x2() - road.x1();
        double dy = road.y2() - road.y1();
        double lengthOnMap = Math.hypot(dx, dy);
        double fraction = Math.max(0.0, Math.min(1.0,
                distanceMeters / road.lengthMeters()));

        // Unit vector perpendicular to the road's direction.
        double normalX = -dy / lengthOnMap;
        double normalY = dx / lengthOnMap;
        double offset = (laneIndex - (road.laneCount() - 1) / 2.0)
                * LANE_SPACING;

        return new Point2D.Double(
                road.x1() + fraction * dx + normalX * offset,
                road.y1() + fraction * dy + normalY * offset);
    }

    public static double directionRadians(RoadView road) {
        Objects.requireNonNull(road, "road");
        return Math.atan2(road.y2() - road.y1(), road.x2() - road.x1());
    }
}
