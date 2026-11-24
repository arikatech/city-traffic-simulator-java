package com.smartcity.simulator.city;

public interface EntryBlockable {

    void blockEntry(String reason);

    void unblockEntry(String reason);

    boolean isEntryOpen();
}