package com.smartcity.simulator.vehicles;

import com.smartcity.simulator.city.Lane;

public final class Ambulance extends EmergencyVehicle {

    public Ambulance(String id, Lane startingLane) {
        super(id, startingLane);
    }

    @Override
    public double getPhysicalMaximumSpeed() {
        return 30.0;
    }
}