package com.smartcity.simulator.city;

import java.util.*;

public final class Road implements EntryBlockable {
    private final String id;
    private final double lengthMeters;
    private double baseSpeedLimit;
    private final Set<String> entryBlockReasons = new HashSet<>();
    
    private final List<Lane> lanes = new ArrayList<>();
    private final List<Road> outgoingRoads = new ArrayList<>();
    
    public Road(String id, double lengthMeters, double baseSpeedLimit){
        if(id==null || id.isBlank()){
            throw new IllegalArgumentException("Road ID cannot be empty.");
        }
        
        if(!Double.isFinite(lengthMeters) || lengthMeters<=0){
            throw new IllegalArgumentException("Road length must be positive and finite.");
        }
        this.id = id;
        this.lengthMeters=lengthMeters;
        
        setBaseSpeedLimit(baseSpeedLimit);
    }
    
    public Lane addLane(String laneId){
        if(laneId==null || laneId.isBlank()){
            throw new IllegalArgumentException("Lane ID cannot be empty.");
        }
        
        for(Lane existing : lanes){
            if(existing.getId().equals(laneId)){
                throw new IllegalArgumentException("Duplicate lane ID: "+laneId);
            }
        }
        
        Lane lane = new Lane(laneId, this);
        lanes.add(lane);
        return lane;
    }
    
    public void setBaseSpeedLimit(double speedLimit){
        if(!Double.isFinite(speedLimit) || speedLimit <= 0){
            throw new IllegalArgumentException("Speed limit must be positive and finite.");
        }
        this.baseSpeedLimit= speedLimit;
    }
    void addOutgoingRoad(Road nextRoad){ //Package-private access (only classes in the same package can access)
        Objects.requireNonNull(nextRoad, "Next road cannot be null");

        if (nextRoad == this) {
            throw new IllegalArgumentException(
                    "A road cannot connect directly to itself");
        }

        if (outgoingRoads.contains(nextRoad)) {
            throw new IllegalArgumentException(
                    "Road connection already exists");
        }

        outgoingRoads.add(nextRoad);   
    }
    public List<Road> getOutgoingRoads(){
        return List.copyOf(outgoingRoads);
    }
    public String getId(){
        return id;
    }
    public double getLengthMeters(){
        return lengthMeters;
    }
    public double getBaseSpeedLimit(){
        return baseSpeedLimit;
    }
    public List<Lane> getLanes(){
        return List.copyOf(lanes);
    }
    
    public void blockEntry(String reason) {
        Objects.requireNonNull(reason, "Block reason cannot be null");

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Block reason cannot be blank");
        }

        entryBlockReasons.add(reason);
    }

    public void unblockEntry(String reason) {
        Objects.requireNonNull(reason);
        entryBlockReasons.remove(reason);
    }

    public boolean isEntryOpen() {
        return entryBlockReasons.isEmpty();
    }
    
    public Set<String> getEntryBlockReasons() {
        return Set.copyOf(entryBlockReasons);
    }

    public boolean hasAvailableEntryLane() {
        return isEntryOpen()
                && lanes.stream().anyMatch(Lane::isEntryOpen);
    }
    
}
