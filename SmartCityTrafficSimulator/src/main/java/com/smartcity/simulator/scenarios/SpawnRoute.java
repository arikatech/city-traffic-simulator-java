package com.smartcity.simulator.scenarios;

import com.smartcity.simulator.city.Road;
import java.util.Objects;

public record SpawnRoute(
        Road entryRoad,
        Road destinationRoad) {

    public SpawnRoute {
        Objects.requireNonNull(entryRoad, "entryRoad");
        Objects.requireNonNull(destinationRoad, "destinationRoad");

        if (entryRoad == destinationRoad) {
            throw new IllegalArgumentException(
                    "Entry and destination must be different roads"
            );
        }
    }
}