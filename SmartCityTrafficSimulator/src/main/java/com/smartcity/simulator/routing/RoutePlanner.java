package com.smartcity.simulator.routing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;

import java.util.*;

public final class RoutePlanner {

    private final CityMap cityMap;

    public RoutePlanner(CityMap cityMap) {
        this.cityMap = Objects.requireNonNull(cityMap);
    }
    
    public List<Road> findRoute(Road start, Road destination){
        Objects.requireNonNull(start);
        Objects.requireNonNull(destination);
        if (!cityMap.getRoads().contains(start)
            || !cityMap.getRoads().contains(destination)) {
            throw new IllegalArgumentException(
                "Both roads must belong to the city map");
        }
        
        Queue<Road> queue = new ArrayDeque<>();
        Set<Road> visited = new HashSet<>();
        Map <Road, Road> previous = new HashMap<>();
        
        queue.add(start);
        visited.add(start);
        
        while(!queue.isEmpty()){
            Road current = queue.remove();
            if(current == destination){
                return reconstructPath(previous, destination);
            }
            for (Road neighbor : current.getOutgoingRoads()) {
                // Closed roads cannot be newly entered.
                if (!neighbor.isEntryOpen()) {
                    continue;
                }

                if (visited.add(neighbor)) {
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }
        return List.of();

    }
    
    private List<Road> reconstructPath(
        Map<Road, Road> previous,
        Road destination){
        
        List<Road> path = new ArrayList<>();
        Road current = destination;
        
        while (current != null){
            path.add(current);
            current = previous.get(current);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }
}