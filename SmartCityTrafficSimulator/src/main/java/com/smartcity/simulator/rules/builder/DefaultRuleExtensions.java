package com.smartcity.simulator.rules.builder;

//import com.smartcity.simulator.rules.actions.AdaptiveWeatherSpeedAction;
//import com.smartcity.simulator.rules.conditions.TrafficJamCondition;

/** Application bootstrap: register new options without editing RuleBuilder switches. */
public final class DefaultRuleExtensions {
    private DefaultRuleExtensions() {}

    public static RuleExtensionRegistry create() {
        RuleExtensionRegistry registry = new RuleExtensionRegistry();
//        registry.registerVehicleCondition(
//                "TRAFFIC_JAM", "Traffic jam: speed <= 3 m/s and vehicle count",
//                ValueKind.NON_NEGATIVE_INTEGER,
//                draft -> new TrafficJamCondition(
//                        RuleExtensionRegistry.numericComparison(draft.operator()),
//                        RuleExtensionRegistry.nonNegativeInteger(draft.rawValue(), "Vehicle count")));
//        registry.registerVehicleAction(
//                "ADAPTIVE_WEATHER_CAP", "Adaptive speed cap: rain 50%, dry 85%",
//                ValueKind.NONE,
//                draft -> new AdaptiveWeatherSpeedAction());
        return registry;
    }
}
