package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.events.SpeedLimitChangeEvent;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.rules.RuleEngine;
import com.smartcity.simulator.rules.builder.*;
import com.smartcity.simulator.scenarios.*;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.ui.RuleBuilderPanel;
import com.smartcity.simulator.ui.RuleManagementPanel;
import com.smartcity.simulator.vehicles.Car;
import com.smartcity.simulator.vehicles.Vehicle;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** End-to-end proof: plugin condition, plugin action, Swing editing, new scenario recipe. */
public final class ExtensibilityRegistryVerification {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static void rulePluginChecks() throws Exception {
        CityMap city = new CityMap();
        Road r1 = new Road("R-1", 200, 15);
        r1.addLane("L-1");
        city.addRoad(r1);
        for (int i = 1; i <= 3; i++) {
            Car car = new Car("C-" + i, r1.getLanes().get(0));
            car.setSpeed(2.0); // slow-moving traffic
            city.addVehicle(car);
        }

        RuleExtensionRegistry extensions = DefaultRuleExtensions.create();
        RegisteredConditionChoice jam = extensions.conditionsFor(TargetCategory.VEHICLE)
                .stream().filter(o -> o instanceof RegisteredConditionChoice r && r.id().equals("TRAFFIC_JAM"))
                .map(o -> (RegisteredConditionChoice)o).findFirst().orElseThrow();
        RegisteredActionChoice adaptive = extensions.actionsFor(TargetCategory.VEHICLE)
                .stream().filter(o -> o instanceof RegisteredActionChoice r && r.id().equals("ADAPTIVE_WEATHER_CAP"))
                .map(o -> (RegisteredActionChoice)o).findFirst().orElseThrow();
        check(jam.supports(TargetCategory.VEHICLE), "Vehicle choice missing");
        check(!jam.supports(TargetCategory.PEDESTRIAN), "Wrong target accepted");
        check(adaptive.supports(TargetCategory.VEHICLE), "Action missing");

        RuleDraft draft = new RuleDraft("Jam adaptive cap", TargetCategory.VEHICLE,
                ScopeOption.ALL, null,
                new RuleDraft.ConditionDraft(jam, RuleOperator.GREATER_OR_EQUAL, "3", false),
                null, null, new RuleDraft.ActionDraft(adaptive, null), "5", true);
        RuleBuilder builder = new RuleBuilder(city, extensions);
        CompiledRule compiled = builder.build("EXT-1", draft);
        check(compiled instanceof CompiledRule.VehicleEntry, "Must build a Vehicle rule");
        RuleEngine engine = new RuleEngine(city);
        check(engine.getVehicleRules().isEmpty(), "Compilation must not register");
        engine.addVehicleRule(((CompiledRule.VehicleEntry)compiled).rule());
        SimulationClock clock = new SimulationClock(1);
        var rainy = engine.evaluate(SimulationContext.capture(clock, city, Weather.RAIN)).resolve();
        var dry = engine.evaluate(SimulationContext.capture(clock, city, Weather.DRY)).resolve();
        check(rainy.speedFractionFor("C-1") == 0.50, "Rain adaptive cap not applied");
        check(dry.speedFractionFor("C-1") == 0.85, "Dry adaptive cap not applied");

        // The actual Swing editor must round-trip these newly registered choices.
        AtomicReference<CompiledRule> submission = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RuleBuilderPanel panel = new RuleBuilderPanel(city, () -> "UI-EXT", extensions,
                    (id, prepared, result) -> submission.set(result));
            panel.loadDraft(draft);
            check(panel.readDraft().firstCondition().condition().equals(jam), "GUI condition round trip");
            check(panel.readDraft().action().action().equals(adaptive), "GUI action round trip");
            check(panel.compileAndSubmit(), "GUI submission failed: " + panel.getFeedbackText());
        });
        check(submission.get() instanceof CompiledRule.VehicleEntry, "UI did not compile plugin rule");

        // Also prove the default desktop management panel wires in the plugins itself.
        SimulationEngine desktopEngine = new SimulationEngine(new SimulationClock(1.0), city);
        SwingUtilities.invokeAndWait(() -> {
            RuleManagementPanel manager = new RuleManagementPanel(desktopEngine, city, () -> "GUI-DEFAULT");
            RuleBuilderPanel editor = manager.getBuilderPanel();
            editor.loadDraft(draft);
            check(editor.readDraft().firstCondition().condition().equals(jam),
                    "Default manager did not expose condition plugin");
            check(editor.readDraft().action().action().equals(adaptive),
                    "Default manager did not expose action plugin");
            check(editor.compileAndSubmit(), "Default manager cannot compile extension");
            manager.refreshFromEngine();
            check(desktopEngine.getRuleSummaries().size() == 1,
                    "Default manager did not register extension");
        });
        System.out.println("Rule extension: new condition + action + Swing editor + runtime effects passed");
    }

    private static void scenarioPluginChecks() {
        CityMap city = new CityMap();
        Road r1 = new Road("R-1", 500, 15);
        r1.addLane("R-1-L1");
        Road r2 = new Road("R-2", 500, 15);
        r2.addLane("R-2-L1");
        city.addRoad(r1);
        city.addRoad(r2);
        city.connectRoads(r1, r2);
        Car car = new Car("C-1", r1.getLanes().get(0));
        car.assignRoute(new Route(List.of(r1, r2)));
        car.setSpeed(12);
        city.addVehicle(car);
        SimulationEngine engine = new SimulationEngine(new SimulationClock(1), city);
        ScenarioDefinition speedZone = new ScenarioDefinition("SPEED_ZONE", 0.0, true,
                (simulation, map, targetRoad) -> simulation.scheduleEvent(
                        new SpeedLimitChangeEvent("SPEED-ZONE-START", 2.0, targetRoad, 8.0)));
        ScenarioInstaller.install(speedZone, engine, city, List.of(new SpawnRoute(r1, r2)), r1, 42L);
        check(r1.getBaseSpeedLimit() == 15.0, "Premature event");
        engine.step();
        engine.step();
        check(r1.getBaseSpeedLimit() == 15.0, "Event began before 2 seconds");
        double before = car.getPositionMeters();
        engine.step();
        check(r1.getBaseSpeedLimit() == 8.0, "New scenario event was not activated");
        check(Math.abs(car.getPositionMeters() - before - 8.0) < 1e-9,
                "Existing vehicle plan did not use new speed limit");
        System.out.println("Scenario extension: SPEED_ZONE installed without ScenarioInstaller switch passed");
    }

    public static void main(String[] args) throws Exception {
        rulePluginChecks();
        scenarioPluginChecks();
        System.out.println("EXTENSIBILITY REGISTRY VERIFIED (" + checks + " checks)");
    }
}
