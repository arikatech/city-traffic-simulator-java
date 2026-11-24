package com.smartcity.simulator.ui.desktop;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.scenarios.ScenarioPreset;
import com.smartcity.simulator.simulation.SimulationEngine;
import com.smartcity.simulator.simulation.view.MapSnapshot;
import java.util.List;

/** One isolated simulation run. A new instance represents a true reset. */
public interface DesktopSession {
    ScenarioPreset scenario();
    CityMap city();
    SimulationEngine engine();
    double fixedStepSeconds();
    double elapsedSeconds();
    long tickCount();
    String weatherLabel();
    MapSnapshot publishMap();
    void step();
    List<SpawnKind> supportedSpawnTypes();
    /** Returns an explanation of the result, or throws for invalid requests. */
    String spawn(SpawnKind type);
}
