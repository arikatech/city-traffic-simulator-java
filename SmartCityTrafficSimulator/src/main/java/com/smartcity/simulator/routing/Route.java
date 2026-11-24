/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcity.simulator.routing;

import com.smartcity.simulator.city.Road;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author arika
 */
public final class Route {
    private final List<Road> roads;
    public Route(List<Road> roads){
        Objects.requireNonNull(roads, "Route cannot be null");
        if(roads.isEmpty()){
            throw new IllegalArgumentException("A route must contain at least one road");
            
        }
        
        //Validate each road and its connection to the next.
         for (int i = 0; i < roads.size(); i++) {

            Road current = Objects.requireNonNull(
                    roads.get(i), "Route cannot contain null roads");

            if (i > 0) {
                Road previous = roads.get(i - 1);

                if (!previous.getOutgoingRoads().contains(current)) {
                    throw new IllegalArgumentException(
                            "Route contains disconnected roads");
                }
            }
        }

        this.roads = List.copyOf(roads);
    }
    
    public Road getStartRoad() {
        return roads.get(0);
    }

    public Road getDestinationRoad() {
        return roads.get(roads.size() - 1);
    }

    public Road getRoadAt(int index) {
        return roads.get(index);
    }

    public int size() {
        return roads.size();
    }

    public List<Road> getRoads() {
        return roads;
    }
    
}
