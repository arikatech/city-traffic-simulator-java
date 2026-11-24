//package com.smartcity.simulator.rules.actions;
//
//import com.smartcity.simulator.environment.Weather;
//import com.smartcity.simulator.rules.Action;
//import com.smartcity.simulator.rules.RuleEffects;
//import com.smartcity.simulator.simulation.SimulationContext;
//import com.smartcity.simulator.vehicles.Vehicle;
//
///** An action that collects a weather-adaptive speed cap, not a direct state mutation. */
//public final class AdaptiveWeatherSpeedAction implements Action<Vehicle> {
//    @Override
//    public void collect(Vehicle target, SimulationContext context, RuleEffects effects) {
//        context.getRuleObservations().vehicle(target.getId());
//        double fraction = context.getWeather() == Weather.RAIN ? 0.50 : 0.85;
//        effects.limitVehicleSpeed(target.getId(), fraction);
//    }
//}
