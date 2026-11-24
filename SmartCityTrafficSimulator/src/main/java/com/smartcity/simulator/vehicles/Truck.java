package com.smartcity.simulator.vehicles;

import com.smartcity.simulator.city.Lane;

public final class Truck extends Vehicle {

    public Truck(String id, Lane startingLane) {
        this(id, startingLane, 12.0);
    }

    public Truck(
            String id,
            Lane startingLane,
            double lengthMeters) {

        super(id, startingLane, lengthMeters);
    }

    @Override
    public double getPhysicalMaximumSpeed() {
        return 20.0;
    }
}