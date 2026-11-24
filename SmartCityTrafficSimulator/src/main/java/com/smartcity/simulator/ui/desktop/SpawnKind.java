package com.smartcity.simulator.ui.desktop;

/** Supported manually spawned traffic participants. */
public enum SpawnKind {
    CAR("Car", "com.smartcity.simulator.vehicles.Car"),
    BUS("Bus", "com.smartcity.simulator.vehicles.Bus"),
    TRUCK("Truck", "com.smartcity.simulator.vehicles.Truck"),
    AMBULANCE("Ambulance", "com.smartcity.simulator.vehicles.Ambulance"),
    POLICE_CAR("Police car", "com.smartcity.simulator.vehicles.PoliceCar"),
    FIRE_TRUCK("Fire truck", "com.smartcity.simulator.vehicles.FireTruck"),
    PEDESTRIAN("Pedestrian", "com.smartcity.simulator.pedestrians.Pedestrian");

    private final String label;
    private final String className;
    SpawnKind(String label, String className) {
        this.label = label;
        this.className = className;
    }
    public String className() { return className; }
    @Override public String toString() { return label; }
}