/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcity.simulator.vehicles;
import com.smartcity.simulator.city.Lane;
/**
 *
 * @author arika
 */
public final class Car extends Vehicle{
    private static final double MAX_SPEED_MPS = 30.0;
    
    public Car(String id, Lane startingLane){
        super(id, startingLane);
    }
    
    @Override
    public double getPhysicalMaximumSpeed(){
        return MAX_SPEED_MPS;
    }
    
}
