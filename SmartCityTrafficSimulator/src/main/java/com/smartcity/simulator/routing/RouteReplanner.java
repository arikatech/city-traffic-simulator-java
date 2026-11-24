package com.smartcity.simulator.routing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.List;
import java.util.Objects;

public final class RouteReplanner {

    private final RoutePlanner planner;

    public RouteReplanner(CityMap city) {
        this.planner = new RoutePlanner(
                Objects.requireNonNull(city)
        );
    }

    public boolean ensureUsableRoute(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");

        if (!vehicle.hasRoute()) {
            return true;
        }

        Route currentRoute = vehicle.getRoute();

        boolean futureRoadClosed = false;

        for (int i = vehicle.getRouteIndex() + 1;
                i < currentRoute.size(); i++) {

            if (!currentRoute.getRoadAt(i)
                    .hasAvailableEntryLane()) {

                futureRoadClosed = true;
                break;
            }
        }

        if (!futureRoadClosed) {
            return true;
        }

        List<Road> replacement = planner.findRoute(
                vehicle.getCurrentRoad(),
                currentRoute.getDestinationRoad()
        );

        if (replacement.isEmpty()) {
            return false;
        }

        vehicle.assignRoute(new Route(replacement));

        return true;
    }
}