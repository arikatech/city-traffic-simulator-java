package com.smartcity.simulator.rules.builder;

import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.pedestrians.Pedestrian;
import com.smartcity.simulator.rules.Rule;
import com.smartcity.simulator.signals.TrafficLight;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.Objects;

// A type-safe result: Rule<Vehicle> never becomes Rule<Pedestrian>.
public sealed interface CompiledRule permits
        CompiledRule.VehicleEntry,
        CompiledRule.PedestrianEntry,
        CompiledRule.LightEntry,
        CompiledRule.RoadEntry {

    record VehicleEntry(Rule<Vehicle> rule) implements CompiledRule {
        public VehicleEntry { Objects.requireNonNull(rule); }
    }

    record PedestrianEntry(Rule<Pedestrian> rule) implements CompiledRule {
        public PedestrianEntry { Objects.requireNonNull(rule); }
    }

    record LightEntry(Rule<TrafficLight> rule) implements CompiledRule {
        public LightEntry { Objects.requireNonNull(rule); }
    }

    record RoadEntry(Rule<Road> rule) implements CompiledRule {
        public RoadEntry { Objects.requireNonNull(rule); }
    }
}
