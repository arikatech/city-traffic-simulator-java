/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcity.simulator.entities;

/**
 *
 * @author arika
 */
public abstract class SimulationEntity {
    private final String id;
    
    protected SimulationEntity(String id){
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                "Entity ID cannot be empty."
            );
        }
        this.id= id;
    }
    
    public String getId(){
        return id;
    }
    
}
