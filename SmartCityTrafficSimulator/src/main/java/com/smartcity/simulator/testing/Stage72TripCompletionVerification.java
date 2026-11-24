package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.simulation.CompletedTrip;
import com.smartcity.simulator.simulation.SimulationClock;
import com.smartcity.simulator.simulation.SimulationEngine;
import com.smartcity.simulator.vehicles.Car;
import java.util.List;

/**
 * Run File in NetBeans after copying the Stage 7.2 trip-completion fix.
 * Uses public domain APIs, not Java2D or Swing.
 */
public final class Stage72TripCompletionVerification {

    private static int checks;

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }

    private static Road road(CityMap city, String id, double metres) {
        Road road = new Road(id, metres, 10.0);
        road.addLane(id + "-L1");
        city.addRoad(road);
        return road;
    }

    private static Car car(String id, Road road) {
        Car car = new Car(id, road.getLanes().get(0));
        car.setSpeed(Math.min(5.0, car.getPhysicalMaximumSpeed()));
        return car;
    }

    public static void main(String[] args) {
        // ARRIVAL: completing the final road removes the car exactly once.
        CityMap city = new CityMap();
        Road destination = road(city, "DEST", 20.0);
        Car arrived = car("ARRIVE-1", destination);
        arrived.assignRoute(new Route(List.of(destination)));
        city.addVehicle(arrived);
        SimulationEngine engine = new SimulationEngine(
                new SimulationClock(1.0), city);

        check(engine.getCompletedTripCount() == 0, "Initially no trips");
        check(city.getMobileEntities().size() == 1, "Initially one car");

        int steps = 0;
        while (city.getMobileEntities().contains(arrived) && steps < 200) {
            engine.step();
            steps++;
        }
        check(steps < 200, "Car must eventually arrive");
        check(city.getMobileEntities().isEmpty(), "Arrival removes car");
        check(engine.getCompletedTripCount() == 1,
                "Arrival recorded once");

        CompletedTrip record = engine.getCompletedTrips().get(0);
        check("ARRIVE-1".equals(record.vehicleId()), "Correct ID");
        check("Car".equals(record.vehicleType()), "Concrete type");
        check(record.travelTimeSeconds() > 0, "Positive travel time");
        check(!record.emergency(), "Ordinary car is not emergency");

        engine.step();
        check(engine.getCompletedTripCount() == 1, "No double counting");
        check(!city.removeVehicle(arrived), "Already removed");

        // INTERMEDIATE ROAD: reaching its endpoint is NOT trip completion.
        CityMap anotherCity = new CityMap();
        Road firstRoad = road(anotherCity, "FIRST", 20.0);
        Road lastRoad = road(anotherCity, "LAST", 30.0);
        anotherCity.connectRoads(firstRoad, lastRoad);
        Car travelling = car("IN-TRANSIT", firstRoad);
        travelling.assignRoute(new Route(List.of(firstRoad, lastRoad)));
        anotherCity.addVehicle(travelling);
        SimulationEngine anotherEngine = new SimulationEngine(
                new SimulationClock(1.0), anotherCity);

        anotherEngine.step();
        check(anotherCity.getMobileEntities().contains(travelling),
                "Intermediate-road car still exists");
        check(anotherEngine.getCompletedTripCount() == 0,
                "Intermediate road not counted as completion");

        // Completed-trip snapshots cannot be changed by external callers.
        try {
            engine.getCompletedTrips().clear();
            throw new AssertionError("History should be read-only");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }

        System.out.println("STAGE 7.2 TRIP COMPLETION PASSED! ("
                + checks + " checks)");
    }
}
