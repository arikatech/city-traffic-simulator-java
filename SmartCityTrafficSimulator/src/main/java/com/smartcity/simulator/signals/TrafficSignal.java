package com.smartcity.simulator.signals;

public interface TrafficSignal {

    SignalState getState();

    boolean canProceed();
}