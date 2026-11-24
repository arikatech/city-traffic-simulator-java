//package com.smartcity.simulator.rules.conditions;
//
//import com.smartcity.simulator.rules.Condition;
//import com.smartcity.simulator.rules.InvalidRuleException;
//import com.smartcity.simulator.simulation.SimulationContext;
//import com.smartcity.simulator.simulation.RuleObservations;
//import com.smartcity.simulator.vehicles.Vehicle;
//import java.util.Objects;
//
///** Example externally registered rule component: slow vehicle AND busy road. */
//public final class TrafficJamCondition implements Condition<Vehicle> {
//    private static final double SLOW_SPEED_MPS = 3.0;
//    private final Comparison countComparison;
//    private final int vehicleThreshold;
//
//    public TrafficJamCondition(Comparison countComparison, int vehicleThreshold) {
//        this.countComparison = Objects.requireNonNull(countComparison);
//        if (vehicleThreshold < 0) {
//            throw new InvalidRuleException("Traffic density threshold cannot be negative");
//        }
//        this.vehicleThreshold = vehicleThreshold;
//    }
//
//    @Override
//    public boolean evaluate(Vehicle target, SimulationContext context) {
//        RuleObservations.VehicleFacts observed =
//                context.getRuleObservations().vehicle(target.getId());
//        String roadId = observed.roadId();
//        return roadId != null
//                && observed.speedMetersPerSecond() <= SLOW_SPEED_MPS
//                && countComparison.test(context.getVehicleCountOnRoad(roadId), vehicleThreshold);
//    }
//}
