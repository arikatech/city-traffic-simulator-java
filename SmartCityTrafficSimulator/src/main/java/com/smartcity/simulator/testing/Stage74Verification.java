package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.simulation.CompletedTrip;
import com.smartcity.simulator.simulation.SimulationClock;
import com.smartcity.simulator.simulation.SimulationEngine;
import com.smartcity.simulator.statistics.StatisticsCollector;
import com.smartcity.simulator.statistics.StatisticsSnapshot;
import com.smartcity.simulator.ui.StatisticsPanel;
import com.smartcity.simulator.vehicles.Car;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.OptionalDouble;
import javax.swing.SwingUtilities;

/** Stage 7.4: verification of committed outcomes and read-only Swing panel. */
public final class Stage74Verification {
    private static int checks;
    private Stage74Verification() { }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }

    private static void near(double value, double expected, String name) {
        check(Math.abs(value - expected) < 1e-7,
                name + " (got " + value + ", expected " + expected + ")");
    }

    private static Car addCar(CityMap city, Road road, String id, double speed) {
        Car car = new Car(id, road.getLanes().get(0));
        car.assignRoute(new Route(List.of(road)));
        car.setSpeed(speed);
        city.addVehicle(car);
        return car;
    }

    public static void main(String[] args) throws Exception {
        CityMap city = new CityMap();
        Road road = new Road("STAT-ROAD", 20.0, 10.0);
        road.addLane("STAT-LANE");
        city.addRoad(road);
        Car car = addCar(city, road, "STAT-CAR", 0.0);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(1.0), city);

        StatisticsSnapshot empty = engine.getStatisticsSnapshot();
        check(empty.completedTrips() == 0, "Initially zero completed trips");
        check(empty.meanTravelTimeSeconds().isEmpty(), "No invented initial mean");
        check(empty.meanEmergencyTripTimeSeconds().isEmpty(), "No invented emergency mean");
        near(empty.cumulativeWaitingSeconds(), 0.0, "No initial waiting");
        check(empty.currentQueueLength() == 0, "No queue before first committed tick");
        check(empty.successfulPedestrianCrossings() == 0, "No invented crossings");

        engine.step();
        StatisticsSnapshot stopped = engine.getStatisticsSnapshot();
        near(stopped.vehicleWaitingSeconds(), 1.0, "A committed stationary tick counts one second");
        near(stopped.cumulativeWaitingSeconds(), 1.0, "Cumulative waiting includes vehicle waiting");
        check(stopped.currentQueueLength() == 1, "Stationary vehicle enters current queue");
        check(stopped.completedTrips() == 0, "Stopped car not completed");
        engine.getStatisticsSnapshot(); // Reading / rendering cannot increment counters.
        near(engine.getStatisticsSnapshot().cumulativeWaitingSeconds(), 1.0,
                "Repeated snapshot reads never advance waiting");

        car.setSpeed(5.0);
        engine.step();
        StatisticsSnapshot moving = engine.getStatisticsSnapshot();
        check(car.getPositionMeters() > 0, "Vehicle physically moved");
        near(moving.vehicleWaitingSeconds(), 1.0, "Moving tick does not add waiting");
        check(moving.currentQueueLength() == 0, "Queue clears after movement");

        int n = 0;
        while (engine.getCompletedTripCount() == 0 && n++ < 30) engine.step();
        check(n < 30, "Car must reach its destination");
        check(city.getMobileEntities().isEmpty(), "Completed car leaves CityMap");
        check(engine.getCompletedTripCount() == 1, "Engine retains completed trip");
        StatisticsSnapshot arrived = engine.getStatisticsSnapshot();
        check(arrived.completedTrips() == 1, "Statistics count same committed arrival");
        check(arrived.meanTravelTimeSeconds().isPresent(), "Mean available after completed sample");
        near(arrived.meanTravelTimeSeconds().getAsDouble(),
                engine.getCompletedTrips().get(0).travelTimeSeconds(),
                "Mean equals completed-trip travel time");
        check(arrived.currentQueueLength() == 0, "Arrived car not queued");
        check(arrived.meanEmergencyTripTimeSeconds().isEmpty(),
                "No emergency sample means no emergency mean");
        engine.step();
        check(engine.getStatisticsSnapshot().completedTrips() == 1,
                "No duplicate completion on later ticks");

        // Independent accumulator checks sample averaging and emergency-only mean.
        StatisticsCollector sample = new StatisticsCollector();
        sample.recordCompletedTrip(new CompletedTrip("T1", "Car", 0.0, 8.0, false));
        sample.recordCompletedTrip(new CompletedTrip("T2", "Ambulance", 1.0, 13.0, true));
        sample.recordCompletedTrip(new CompletedTrip("T3", "FireTruck", 0.0, 6.0, true));
        StatisticsSnapshot average = sample.snapshot();
        check(average.completedTrips() == 3, "Three completed samples");
        near(average.meanTravelTimeSeconds().orElseThrow(), (8 + 12 + 6) / 3.0,
                "All-trip mean uses all completed samples");
        check(average.completedEmergencyTrips() == 2, "Two completed emergency samples");
        near(average.meanEmergencyTripTimeSeconds().orElseThrow(), 9.0,
                "Emergency mean uses emergency samples only");

        boolean rejected = false;
        try { new StatisticsSnapshot(0, OptionalDouble.of(0.0),
                    0, 0, 0, 0, 0, 0, OptionalDouble.empty()); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Reject fabricated means without samples");
        rejected = false;
        try { new StatisticsSnapshot(0, OptionalDouble.empty(),
                    Double.NaN, Double.NaN, 0, 0, 0, 0, OptionalDouble.empty()); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Reject NaN measurements");

        SwingUtilities.invokeAndWait(() -> {
            StatisticsPanel panel = new StatisticsPanel();
            panel.setSize(900, 92);
            panel.showStatistics(empty);
            check("No trips".equals(panel.valueForTest(1)),
                    "UI marks unobserved mean as No trips");
            check("No trips".equals(panel.valueForTest(5)),
                    "UI marks unobserved emergency mean as No trips");
            panel.showStatistics(average);
            check("3".equals(panel.valueForTest(0)), "UI displays completed sample count");
            BufferedImage image = new BufferedImage(900, 92, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try { panel.paint(g); } finally { g.dispose(); }
            check(sample.snapshot().completedTrips() == 3,
                    "Painting must not modify metric counts");
        });

        // New engine == reset: no persisted statistics from previous runs.
        SimulationEngine fresh = new SimulationEngine(new SimulationClock(1.0), new CityMap());
        check(fresh.getStatisticsSnapshot().completedTrips() == 0, "New session resets completed count");
        near(fresh.getStatisticsSnapshot().cumulativeWaitingSeconds(), 0, "New session resets waiting");
        check(fresh.getStatisticsSnapshot().currentQueueLength() == 0, "New session resets queue");

        System.out.println("STAGE 7.4 VERIFICATION PASSED! (" + checks + " checks)");
    }
}
