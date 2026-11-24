package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.rules.builder.*;
import com.smartcity.simulator.rules.builder.RuleDraft.ActionDraft;
import com.smartcity.simulator.rules.builder.RuleDraft.ConditionDraft;
import com.smartcity.simulator.ui.RuleBuilderPanel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Runs without opening a window. ALL Swing access stays on the EDT. */
public final class Stage63RuleBuilderPanelTest {

    private static int checks;

    private static void check(boolean okay, String message) {
        if (!okay) throw new AssertionError(message);
        checks++;
    }

    private static RuleDraft rainRule() {
        return new RuleDraft("Rain congestion cap", TargetCategory.VEHICLE,
                ScopeOption.VEHICLES_ON_ROAD, "R-1",
                new ConditionDraft(ConditionOption.VEHICLE_COUNT_ON_ROAD,
                        RuleOperator.GREATER_THAN, "10", false),
                new ConditionDraft(ConditionOption.WEATHER,
                        RuleOperator.EQUAL, "RAIN", true),
                RuleDraft.Connector.AND,
                new ActionDraft(ActionOption.LIMIT_SPEED, "0.8"),
                "5", true);
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(Stage63RuleBuilderPanelTest::runChecks);
        System.out.println("STAGE 6.3 PASSED! (" + checks + " checks)");
    }

    private static void runChecks() {
        CityMap city = new CityMap();
        Road r1 = new Road("R-1", 100, 10);
        Road r2 = new Road("R-2", 100, 10);
        city.addRoad(r1);
        city.addRoad(r2);
        city.addIntersection(new Intersection("J-1", List.of(r1), 8, 2, 1));

        AtomicInteger preparedCount = new AtomicInteger();
        AtomicReference<String> lastId = new AtomicReference<>();
        AtomicReference<RuleDraft> lastDraft = new AtomicReference<>();
        AtomicReference<CompiledRule> lastRule = new AtomicReference<>();

        RuleBuilderPanel panel = new RuleBuilderPanel(city, () -> "USER-NEW", (id, draft, rule) -> {
            preparedCount.incrementAndGet();
            lastId.set(id);
            lastDraft.set(draft);
            lastRule.set(rule);
        });

        panel.loadDraft(rainRule());
        RuleDraft read = panel.readDraft();
        check(read.name().equals("Rain congestion cap"), "Name round trip");
        check(read.target() == TargetCategory.VEHICLE, "Target round trip");
        check(read.scope() == ScopeOption.VEHICLES_ON_ROAD, "Scope round trip");
        check("R-1".equals(read.scopeId()), "Real road ID round trip");
        check(read.firstCondition().condition() == ConditionOption.VEHICLE_COUNT_ON_ROAD,
                "First condition round trip");
        check(read.firstCondition().operator() == RuleOperator.GREATER_THAN,
                "First operator round trip");
        check(read.firstCondition().rawValue().equals("10"), "First value round trip");
        check(read.secondCondition().condition() == ConditionOption.WEATHER,
                "Second condition round trip");
        check(read.secondCondition().negated(), "NOT selection round trip");
        check(read.connector() == RuleDraft.Connector.AND, "AND selected");
        check(read.action().action() == ActionOption.LIMIT_SPEED, "Action round trip");
        check(read.action().rawValue().equals("0.8"), "Fraction round trip");
        check(read.priority().equals("5"), "Priority round trip");
        check(read.enabled(), "Enabled round trip");
        check(panel.compileAndSubmit(), "Valid rule should compile");
        check(preparedCount.get() == 1, "Callback invoked once");
        check("USER-NEW".equals(lastId.get()), "ID supplier used");
        check(lastRule.get() instanceof CompiledRule.VehicleEntry,
                "Correct typed vehicle rule");
        check(panel.getFeedbackText().contains("not registered"),
                "Must not falsely claim registration");

        RuleDraft bad = new RuleDraft("Bad fraction", TargetCategory.VEHICLE,
                ScopeOption.ALL, null,
                new ConditionDraft(ConditionOption.VEHICLE_SPEED,
                        RuleOperator.GREATER_THAN, "NaN", false),
                null, null,
                new ActionDraft(ActionOption.LIMIT_SPEED, "0.8"), "5", true);
        panel.loadDraft(bad);
        check(!panel.compileAndSubmit(), "Invalid NaN rejected");
        check(panel.getFeedbackText().contains("finite"), "Specific validation feedback");
        check(preparedCount.get() == 1, "Invalid input cannot submit");

        RuleDraft road = new RuleDraft("Local closure", TargetCategory.ROAD,
                ScopeOption.ROAD_BY_ID, "R-2",
                new ConditionDraft(ConditionOption.WEATHER,
                        RuleOperator.EQUAL, "RAIN", false),
                null, null,
                new ActionDraft(ActionOption.CLOSE_ROAD_ENTRY, null), "4", false);
        panel.loadDraft(road);
        RuleDraft roadRead = panel.readDraft();
        check(roadRead.target() == TargetCategory.ROAD, "Target category changed");
        check(roadRead.scopeId().equals("R-2"), "Second road selectable");
        check(roadRead.secondCondition() == null, "Optional condition absent");
        check(roadRead.connector() == null, "No connector when one condition");
        check(roadRead.action().rawValue() == null, "No parameter for road closure");
        check(!roadRead.enabled(), "Disabled state round trip");
        check(panel.compileAndSubmit(), "Road rule compiled");
        check(lastRule.get() instanceof CompiledRule.RoadEntry, "Typed road rule");

        RuleDraft pedestrian = new RuleDraft("Crossing priority", TargetCategory.PEDESTRIAN,
                ScopeOption.PEDESTRIANS_AT_INTERSECTION, "J-1",
                new ConditionDraft(ConditionOption.PEDESTRIAN_WAITING_TIME,
                        RuleOperator.GREATER_OR_EQUAL, "12", false),
                null, null,
                new ActionDraft(ActionOption.REQUEST_PEDESTRIAN_PRIORITY, null), "9", true);
        panel.loadDraft(pedestrian);
        check(panel.readDraft().scopeId().equals("J-1"), "Intersection ID from map");
        check(panel.compileAndSubmit(), "Pedestrian rule compiled");
        check(lastRule.get() instanceof CompiledRule.PedestrianEntry,
                "Typed pedestrian rule");

        RuleDraft light = new RuleDraft("Signal phase", TargetCategory.TRAFFIC_LIGHT,
                ScopeOption.ALL, null,
                new ConditionDraft(ConditionOption.EMERGENCY_AT_INTERSECTION,
                        null, null, false), null, null,
                new ActionDraft(ActionOption.REQUEST_SIGNAL_PHASE, "GREEN"), "2", true);
        panel.loadDraft(light);
        check(panel.readDraft().firstCondition().operator() == null,
                "Presence has no operator");
        check(panel.readDraft().action().rawValue().equals("GREEN"),
                "Signal enum parameter round trip");
        check(panel.compileAndSubmit(), "Traffic light rule compiled");
        check(lastRule.get() instanceof CompiledRule.LightEntry, "Typed light rule");

        panel.loadForEditing("USER-EXISTING", rainRule());
        check(panel.compileAndSubmit(), "Valid edit prepared");
        check(lastId.get().equals("USER-EXISTING"),
                "Edit must reuse original rule ID");
        check(lastDraft.get().secondCondition().negated(),
                "Edit preserves NOT");
        check(panel.getFeedbackText().contains("not applied"),
                "Edit must not falsely claim application");
        check(preparedCount.get() == 5, "Only five valid submissions");

        panel.clearForm();
        check(panel.readDraft().target() == TargetCategory.VEHICLE,
                "Clear resets target");
        check(panel.readDraft().secondCondition() == null,
                "Clear removes second condition");
        check(!panel.compileAndSubmit(), "Empty name rejected after clear");
        check(preparedCount.get() == 5, "Clear must not submit");
    }
}
