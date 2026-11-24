/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcity.simulator.city;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author arika
 */
public final class Lane implements EntryBlockable {
    private final String id;
    private final Road road;
    private final Set<String> entryBlockReasons = new HashSet<>();

    
    Lane(String id, Road road){
        this.id=id;
        this.road=road;
    }
    public String getId(){
        return id;
    }
    public Road getRoad(){
        return road;
    }
    
    @Override
    public void blockEntry(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Block reason cannot be empty");
        }
        entryBlockReasons.add(reason);
    }

    @Override
    public void unblockEntry(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Block reason cannot be empty");
        }
        entryBlockReasons.remove(reason);
    }

    @Override
    public boolean isEntryOpen() {
        return entryBlockReasons.isEmpty();
    }

    public Set<String> getEntryBlockReasons() {
        return Set.copyOf(entryBlockReasons);
    }
}
