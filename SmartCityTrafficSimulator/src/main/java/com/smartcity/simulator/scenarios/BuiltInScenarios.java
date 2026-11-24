package com.smartcity.simulator.scenarios;

import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.events.AccidentEvent;
import com.smartcity.simulator.events.RoadworkEvent;
import com.smartcity.simulator.events.WeatherChangeEvent;
import com.smartcity.simulator.events.SpeedLimitChangeEvent;
import java.util.Map;
import java.util.Objects;

/** Only the predefined scenario recipes belong here; installer is agnostic of types. */
public final class BuiltInScenarios {
    private static final Map<ScenarioPreset, ScenarioDefinition> BUILT_INS = Map.of(
            ScenarioPreset.NORMAL_TRAFFIC,
            new ScenarioDefinition("NORMAL_TRAFFIC", ScenarioPreset.NORMAL_TRAFFIC.getCarsPerSecond(), false, (engine, city, road) -> {}),
            ScenarioPreset.RUSH_HOUR,
            new ScenarioDefinition("RUSH_HOUR", ScenarioPreset.RUSH_HOUR.getCarsPerSecond(), false, (engine, city, road) -> {}),
            ScenarioPreset.RAIN,
            new ScenarioDefinition("RAIN", ScenarioPreset.RAIN.getCarsPerSecond(), false, (engine, city, road) -> {
                engine.scheduleEvent(new WeatherChangeEvent("SCENARIO-RAIN-START", 10.0, Weather.RAIN));
                engine.scheduleEvent(new WeatherChangeEvent("SCENARIO-RAIN-END", 70.0, Weather.DRY));
            }),
            ScenarioPreset.ROADWORK,
            new ScenarioDefinition("ROADWORK", ScenarioPreset.ROADWORK.getCarsPerSecond(), true, (engine, city, road) ->
                    engine.scheduleEvent(new RoadworkEvent("SCENARIO-ROADWORK", 12.0, 75.0, road))),
            ScenarioPreset.ACCIDENT,
            new ScenarioDefinition("ACCIDENT", ScenarioPreset.ACCIDENT.getCarsPerSecond(), true, (engine, city, road) ->
                    engine.scheduleEvent(new AccidentEvent("SCENARIO-ACCIDENT", 20.0, 60.0, road)))
//            ScenarioPreset.SLIPPERY_ROAD,
//            new ScenarioDefinition(
//                "SLIPPERY_ROAD",
//                ScenarioPreset.SLIPPERY_ROAD.getCarsPerSecond(),
//                false,
//                (engine, city, road) -> {
//                    for (Road targetRoad : city.getRoads()) {
//                        engine.scheduleEvent(
//                            new SpeedLimitChangeEvent(
//                                "SLIPPERY-" + targetRoad.getId(),
//                                20.0,
//                                targetRoad,
//                                8.0
//                            )
//                        );
//                    }
//                }
//            )
            );

    private BuiltInScenarios() {}

    public static ScenarioDefinition forPreset(ScenarioPreset preset) {
        return Objects.requireNonNull(BUILT_INS.get(Objects.requireNonNull(preset)),
                "No recipe defined for scenario preset " + preset);
    }
}