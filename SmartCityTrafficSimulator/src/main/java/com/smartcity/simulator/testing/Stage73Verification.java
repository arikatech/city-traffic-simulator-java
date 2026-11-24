package com.smartcity.simulator.testing;

import com.smartcity.simulator.simulation.view.InspectionSnapshot;
import com.smartcity.simulator.simulation.view.MapSnapshot;
import com.smartcity.simulator.simulation.view.MapSnapshot.*;
import com.smartcity.simulator.ui.CityMapPanel;
import com.smartcity.simulator.ui.EntityInspector;
import com.smartcity.simulator.ui.map.LaneProjection;
import com.smartcity.simulator.ui.map.MapHitTester;
import com.smartcity.simulator.ui.map.MapSelection;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/** Headless-safe geometry/selection/rendering checks, no external dependencies. */
public final class Stage73Verification {
    private static int passed;
    private Stage73Verification() { }

    public static void main(String[] args) throws Exception {
        RoadView road = new RoadView("R", 50, 100, 350, 100, 200, 2, false);
        MapSnapshot snapshot = new MapSnapshot(400, 220,
                List.of(road), List.of(new IntersectionView("J",350,100)),
                List.of(new CrosswalkView("CW", 180,100,true)),
                List.of(new BusStopView("BUS",200,60)),
                List.of(new SignalView("TL",340,70,SignalColor.GREEN)),
                List.of(new VehicleView("CAR-1",VehicleKind.CAR,"R",0,80)),
                List.of(new PedestrianView("P-1",180,85,PedestrianState.WAITING)),
                List.of(new IncidentView("I-1",IncidentKind.ROADWORK,"R")));
        MapHitTester.Viewport viewport = MapHitTester.viewport(snapshot,800,440);
        check(viewport.scale() > 0, "Positive scale");
        check(Math.abs(viewport.offsetX()+viewport.scale()*snapshot.mapWidth()/2.0-400)<0.00001,"Centered x");
        check(Math.abs(viewport.offsetY()+viewport.scale()*snapshot.mapHeight()/2.0-220)<0.00001,"Centered y");
        Point2D p = LaneProjection.positionOnLane(road,0,80);
        Point2D car = viewport.screen(p.getX(),p.getY());
        check(pick(snapshot, 800,440,car).equals(new MapSelection(MapSelection.Kind.VEHICLE,"CAR-1")),"Vehicle hit");
        check(pick(snapshot,800,440,viewport.screen(180,85)).equals(new MapSelection(MapSelection.Kind.PEDESTRIAN,"P-1")),"Pedestrian hit");
        check(pick(snapshot,800,440,viewport.screen(340,70)).equals(new MapSelection(MapSelection.Kind.SIGNAL,"TL")),"Signal hit");
        check(pick(snapshot,800,440,viewport.screen(200,60)).equals(new MapSelection(MapSelection.Kind.BUS_STOP,"BUS")),"Bus stop hit");
        check(pick(snapshot,800,440,viewport.screen(350,100)).equals(new MapSelection(MapSelection.Kind.INTERSECTION,"J")),"Intersection hit");
        check(pick(snapshot,800,440,viewport.screen(90,100)).equals(new MapSelection(MapSelection.Kind.ROAD,"R")),"Road hit");
        check(MapHitTester.pick(snapshot,800,440,2,2) == null,"Blank click clears");
        check(MapHitTester.pick(snapshot,800,440,Double.NaN,40)==null,"NaN ignored");
        check(MapHitTester.pick(snapshot,800,440,Double.POSITIVE_INFINITY,40)==null,"Infinity ignored");
        check(MapHitTester.pick(snapshot,800,440,400,Double.NEGATIVE_INFINITY)==null,"Negative infinity ignored");
        check(new MapSelection(MapSelection.Kind.VEHICLE,"X").equals(
                new MapSelection(MapSelection.Kind.VEHICLE,"X")),"Stable selection identity");
        check(!new MapSelection(MapSelection.Kind.ROAD,"X").equals(
                new MapSelection(MapSelection.Kind.VEHICLE,"X")),"Selection kind is distinct");
        check(InspectionSnapshot.message("Not found","Gone").fields().isEmpty(),"Message is read only");
        check(InspectionSnapshot.message("X","Y").title().equals("X"),"Message data");
        boolean threw = false;
        try { new MapSelection(MapSelection.Kind.SIGNAL, " "); }
        catch (IllegalArgumentException expected) { threw = true; }
        check(threw,"Reject blank selection");
        SwingUtilities.invokeAndWait(() -> {
            CityMapPanel panel = new CityMapPanel(snapshot);
            panel.setSize(800,440);
            panel.setSelection(new MapSelection(MapSelection.Kind.VEHICLE,"CAR-1"));
            check(panel.getSelection().id().equals("CAR-1"),"Panel remembers selection");
            AtomicInteger selections = new AtomicInteger();
            panel.onSelection(s -> selections.incrementAndGet());
            BufferedImage image = new BufferedImage(800,440,BufferedImage.TYPE_INT_ARGB);
            panel.paint(image.createGraphics());
            check(panel.getSnapshot()==snapshot,"Painting preserves same snapshot");
            check(panel.getSelection().id().equals("CAR-1"),"Painting preserves selection");
            check(selections.get() == 0,"Painting never triggers selection callback");
            EntityInspector inspector = new EntityInspector();
            inspector.showInspection(new InspectionSnapshot("Vehicle CAR-1", "Read only",
                    List.of(new InspectionSnapshot.Field("Route", "R → EXIT"))));
            check(inspector.getTitleForTest().equals("Vehicle CAR-1"),"Inspector displays title");
            inspector.showInspection(InspectionSnapshot.message("No longer present", "Trip complete"));
            check(inspector.getTitleForTest().equals("No longer present"),"Inspector handles disappearance");
            panel.setSelection(null);
            check(panel.getSelection()==null,"Selection clears");
        });
        System.out.println("STAGE 7.3 VERIFICATION PASSED! (" + passed + " checks)");
    }
    private static MapSelection pick(MapSnapshot map,int w,int h,Point2D p) {
        return MapHitTester.pick(map,w,h,p.getX(),p.getY());
    }
    private static void check(boolean result,String label) {
        if (!result) throw new AssertionError(label);
        passed++;
    }
}
