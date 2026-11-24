/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcity.simulator.simulation;

/**
 *
 * @author arika
 */
public final class SimulationClock {
    private final double deltaSeconds;
    private long tickCount;
    
    public SimulationClock(double deltaSeconds){
        if(!Double.isFinite(deltaSeconds) || deltaSeconds <=0){
            throw new IllegalArgumentException(
                    "The time must be positive and finite."
            );
        }
        this.deltaSeconds = deltaSeconds;
    }
    
    public double getDeltaSeconds(){
        return deltaSeconds;
    }
    public long getTickCount(){
        return tickCount;
    }
    public double getElapsedSeconds(){
        return tickCount * deltaSeconds;
    }
    public void advance(){
        tickCount++;
    }
    public void reset(){
        tickCount=0;
    }
}
