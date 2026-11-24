package com.smartcity.simulator.vehicles;

import com.smartcity.simulator.city.Lane;

public abstract class EmergencyVehicle extends Vehicle {

    private boolean sirenActive;

    protected EmergencyVehicle(String id, Lane startingLane) {
        super(id, startingLane, 5.0);
    }

    public void setSirenActive(boolean active) {
        sirenActive = active;
    }

    @Override
    public boolean requestsSignalPriority() {
        return sirenActive;
    }
}