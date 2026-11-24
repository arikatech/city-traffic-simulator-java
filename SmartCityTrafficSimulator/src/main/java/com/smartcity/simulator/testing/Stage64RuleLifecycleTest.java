package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.rules.builder.*;
import com.smartcity.simulator.rules.builder.RuleDraft.*;
import com.smartcity.simulator.rules.management.*;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.ui.RuleManagementPanel;
import com.smartcity.simulator.vehicles.Car;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Headless test: every Swing operation is on the EDT, with actual engine steps. */
public final class Stage64RuleLifecycleTest {

    private static int checks;
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks++;
    }

    private static RuleDraft vehicleDraft(String name, String fraction) {
        return new RuleDraft(name, TargetCategory.VEHICLE, ScopeOption.ALL, null,
                new ConditionDraft(ConditionOption.SIMULATION_TIME,
                        RuleOperator.GREATER_OR_EQUAL, "0", false),
                null, null, new ActionDraft(ActionOption.LIMIT_SPEED, fraction),
                "5", true);
    }

    private static RuleDraft roadDraft(String name) {
        return new RuleDraft(name, TargetCategory.ROAD, ScopeOption.ROAD_BY_ID, "R-2",
                new ConditionDraft(ConditionOption.SIMULATION_TIME,
                        RuleOperator.GREATER_OR_EQUAL, "0", false),
                null, null, new ActionDraft(ActionOption.CLOSE_ROAD_ENTRY, null),
                "9", true);
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(Stage64RuleLifecycleTest::run);
        System.out.println("STAGE 6.4 PASSED! (" + checks + " checks)");
    }

    private static void run() {
        CityMap city = new CityMap();
        Road r1 = new Road("R-1", 200, 10);
        Road r2 = new Road("R-2", 200, 10);
        city.addRoad(r1);
        city.addRoad(r2);
        city.connectRoads(r1, r2);
        var l1 = r1.addLane("L-1");
        r2.addLane("L-2");
        Car car = new Car("C-1", l1);
        car.assignRoute(new Route(List.of(r1, r2)));
        car.setSpeed(10);
        city.addVehicle(car);

        SimulationEngine engine = new SimulationEngine(new SimulationClock(1), city);
        AtomicInteger seq = new AtomicInteger();
        RuleManagementPanel panel = new RuleManagementPanel(engine, city,
                () -> "USER-" + seq.incrementAndGet());

        // READY: command applies with no simulated time advancement.
        panel.getBuilderPanel().loadDraft(vehicleDraft("Half speed", "0.5"));
        check(panel.getBuilderPanel().compileAndSubmit(), "Valid GUI rule should compile");
        check(engine.getTickCount() == 0, "READY flush must not tick");
        check(engine.getPendingRuleCommandCount() == 0, "READY flush must clear queue");
        check(engine.getRuleEngine().getVehicleRules().size() == 1, "GUI create registered");
        check(engine.getManagedRuleDraft("USER-1") != null, "Draft retained for editing");
        check(panel.getRuleTable().getRowCount() == 1, "Active table should show rule");
        RuleSummary first = engine.getRuleSummaries().get(0);
        check(first.expression().contains("Simulation time"), "Expression shown");
        check(first.action().contains("0.5"), "Action parameter shown");
        check(first.editable(), "Visual rule editable");
        engine.step();
        check(engine.getLastResolvedRuleEffects().speedFractionFor("C-1") == 0.5,
                "Actual speed cap applied by the engine");
        check(car.getPositionMeters() <= 5.000001, "Movement physically constrained");

        // RUNNING: GUI command waits for next tick, cannot be manually flushed.
        engine.startRunning();
        RuleDraft combined = new RuleDraft("Stricter cap", TargetCategory.VEHICLE,
                ScopeOption.ALL, null,
                new ConditionDraft(ConditionOption.SIMULATION_TIME,
                        RuleOperator.GREATER_OR_EQUAL, "0", false),
                new ConditionDraft(ConditionOption.WEATHER,
                        RuleOperator.EQUAL, "RAIN", true),
                RuleDraft.Connector.AND,
                new ActionDraft(ActionOption.LIMIT_SPEED, "0.3"), "5", true);
        panel.getBuilderPanel().loadDraft(combined);
        check(panel.getBuilderPanel().compileAndSubmit(), "Second rule compiled");
        check(engine.getPendingRuleCommandCount() == 1, "Running: command must be queued");
        check(engine.getRuleEngine().getVehicleRules().size() == 1,
                "Running: must not register mid-tick");
        boolean guarded = false;
        try { engine.applyPendingRuleCommandsWhilePaused(); }
        catch (IllegalStateException expected) { guarded = true; }
        check(guarded, "Cannot flush while running");
        engine.step();
        panel.refreshFromEngine();
        check(engine.getRuleEngine().getVehicleRules().size() == 2,
                "Queued rule registered at tick start");
        check(engine.getLastResolvedRuleEffects().speedFractionFor("C-1") == 0.3,
                "Latest tick includes new stricter cap");
        check(panel.getRuleTable().getRowCount() == 2, "Both active rules visible");
        check(engine.getRuleSummaries().stream().anyMatch(summary ->
                summary.expression().contains("AND NOT")),
                "True user-composed AND/NOT expression visible and executable");

        // PAUSED: edit across categories, preserving ID; no time advance.
        engine.pauseRunning();
        long ticks = engine.getTickCount();
        panel.getBuilderPanel().loadForEditing("USER-1", roadDraft("Close exit"));
        check(panel.getBuilderPanel().compileAndSubmit(), "Cross-target edit compiles");
        check(engine.getTickCount() == ticks, "Paused replacement must not tick");
        check(engine.getRuleEngine().getVehicleRules().size() == 1,
                "Original vehicle rule removed after cross-category edit");
        check(engine.getRuleEngine().getRoadRules().size() == 1,
                "New road category registered");
        check(engine.getRuleEngine().getRoadRules().get(0).getId().equals("USER-1"),
                "Edit preserves ID");
        check(engine.getManagedRuleDraft("USER-1").target() == TargetCategory.ROAD,
                "Stored edit draft updated");
        engine.step();
        check(!r2.isEntryOpen(), "Edited road closure actually affects traffic");
        check(engine.getLastResolvedRuleEffects().speedFractionFor("C-1") == 0.3,
                "Other vehicle cap unaffected");

        // Invalid edit: compilation returns false, existing rules untouched.
        panel.getBuilderPanel().loadForEditing("USER-2", vehicleDraft("Broken", "NaN"));
        check(!panel.getBuilderPanel().compileAndSubmit(), "Invalid edit rejected");
        check(engine.getRuleEngine().getVehicleRules().get(0).getName().equals("Stricter cap"),
                "Invalid edit must preserve original");
        check(engine.getPendingRuleCommandCount() == 0, "Invalid edit not enqueued");

        // Table selection operations: disable, re-enable, delete.
        panel.refreshFromEngine();
        selectById(panel.getRuleTable(), "USER-1");
        panel.toggleSelected();
        check(!engine.getRuleEngine().getRoadRules().get(0).isEnabled(),
                "Pause toggle disables real rule");
        check(engine.getManagedRuleDraft("USER-1") != null
                        && !engine.getManagedRuleDraft("USER-1").enabled(),
                "Stored draft reflects enabled state");
        check(engine.getTickCount() == ticks + 1, "Toggle doesn't advance clock");
        engine.step();
        check(r2.isEntryOpen(), "Disabled closure lifted on next tick");
        panel.refreshFromEngine();
        selectById(panel.getRuleTable(), "USER-1");
        panel.toggleSelected();
        check(engine.getRuleEngine().getRoadRules().get(0).isEnabled(),
                "Re-enable is effective");
        engine.step();
        check(!r2.isEntryOpen(), "Re-enabled closure works");

        r2.blockEntry("event:accident");
        panel.refreshFromEngine();
        selectById(panel.getRuleTable(), "USER-1");
        panel.deleteSelected();
        check(engine.getRuleEngine().getRoadRules().isEmpty(), "Deleted rule no longer registered");
        check(engine.getManagedRuleDraft("USER-1") == null, "Deleted draft removed");
        check(engine.getTickCount() == ticks + 3, "Delete doesn't advance time");
        engine.step();
        check(!r2.isEntryOpen(), "Accident closure survives deleting rule");
        r2.unblockEntry("event:accident");
        check(r2.isEntryOpen(), "Accident removal opens road");

        // Failure acknowledgement: duplicate ID rejected, existing rule intact.
        RuleDraft duplicate = vehicleDraft("Duplicate", "0.9");
        var builder = new RuleBuilder(city);
        engine.enqueueRuleCommand(new RuleCommand.Add("USER-2", duplicate,
                builder.build("USER-2", duplicate)));
        check(engine.applyPendingRuleCommandsWhilePaused() == 1, "One duplicate command processed");
        List<RuleCommandResult> results = engine.drainRuleCommandResults();
        check(results.size() == 1 && !results.get(0).success(),
                "Duplicate rejected with failure acknowledgement");
        check(engine.getRuleEngine().getVehicleRules().get(0).getName().equals("Stricter cap"),
                "Duplicate failure did not replace original");

        // Delete other rule; a new complete combination was created and managed live.
        panel.refreshFromEngine();
        selectById(panel.getRuleTable(), "USER-2");
        panel.deleteSelected();
        check(engine.getRuleSummaries().isEmpty(), "All editor rules removed");
        check(engine.getPendingRuleCommandCount() == 0, "Queue empty at finish");
    }

    private static void selectById(JTable table, String id) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (table.getValueAt(row, 7).equals(id)) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new AssertionError("No table row for " + id);
    }
}
