package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.rules.builder.*;
import java.util.List;

public final class Stage61CatalogueTest {

    private static int checks;

    private static void check(boolean okay, String message) {
        if (!okay) {
            throw new AssertionError(message);
        }
        checks++;
    }

    public static void main(String[] args) {
        testTargetFiltering();
        testOperatorsAndValues();
        testActualMapScopes();
        testDraftAndImmutability();

        System.out.println(
                "STAGE 6.1 PASSED! (" + checks + " checks)");
    }

    private static void testTargetFiltering() {

        for (TargetCategory target :
                TargetCategory.values()) {

            check(
                    !RuleCatalogue.conditionsFor(target).isEmpty(),
                    target + " needs conditions"
            );

            check(
                    !RuleCatalogue.actionsFor(target).isEmpty(),
                    target + " needs actions"
            );

            check(
                    RuleCatalogue.scopesFor(target)
                            .contains(ScopeOption.ALL),
                    target + " needs an unrestricted scope"
            );

            for (ConditionOption condition :
                    RuleCatalogue.conditionsFor(target)) {

                check(condition.supports(target),
                        "Incompatible condition offered");
            }

            for (ActionOption action :
                    RuleCatalogue.actionsFor(target)) {

                check(action.supports(target),
                        "Incompatible action offered");
            }
        }

        check(
                RuleCatalogue.conditionsFor(TargetCategory.VEHICLE)
                        .contains(ConditionOption.VEHICLE_SPEED),
                "Vehicle speed missing"
        );

        check(
                !RuleCatalogue.conditionsFor(TargetCategory.PEDESTRIAN)
                        .contains(ConditionOption.VEHICLE_SPEED),
                "Pedestrian cannot use vehicle speed"
        );

        check(
                !RuleCatalogue.actionsFor(TargetCategory.ROAD)
                        .contains(ActionOption.LIMIT_SPEED),
                "Road cannot execute vehicle speed action"
        );

        check(
                RuleCatalogue.actionsFor(TargetCategory.TRAFFIC_LIGHT)
                        .equals(List.of(
                                ActionOption.REQUEST_SIGNAL_PHASE)),
                "Traffic light action catalogue wrong"
        );
    }

    private static void testOperatorsAndValues() {

        check(
                ConditionOption.VEHICLE_SPEED.getOperators()
                        .contains(RuleOperator.GREATER_THAN),
                "Speed comparison missing"
        );

        check(
                !ConditionOption.WEATHER.getOperators()
                        .contains(RuleOperator.GREATER_THAN),
                "Weather must not use >"
        );

        check(
                ConditionOption.WEATHER.getOperators()
                        .contains(RuleOperator.NOT_EQUAL),
                "Weather != missing"
        );

        check(
                ConditionOption.EMERGENCY_ON_ROAD
                        .getOperators().isEmpty(),
                "Presence condition uses NOT, not an operator"
        );

        check(
                RuleCatalogue.predefinedValues(ValueKind.WEATHER)
                        .equals(List.of("DRY", "RAIN")),
                "Weather options mismatch"
        );

        check(
                !RuleCatalogue.predefinedValues(
                        ValueKind.VEHICLE_FAMILY)
                        .contains("OTHER"),
                "OTHER is not a valid builder value"
        );

        check(
                RuleCatalogue.predefinedValues(
                        ValueKind.SIGNAL_PHASE)
                        .equals(List.of("GREEN", "RED")),
                "YELLOW cannot be requested"
        );

        check(
                ActionOption.LIMIT_SPEED.getValueKind()
                        == ValueKind.SPEED_FRACTION,
                "Speed cap needs fraction"
        );
    }

    private static void testActualMapScopes() {

        CityMap city = new CityMap();

        Road r1 = new Road("R-1", 100, 15);
        Road r2 = new Road("R-2", 100, 15);

        city.addRoad(r1);
        city.addRoad(r2);

        city.addIntersection(
                new Intersection(
                        "J-1",
                        List.of(r1),
                        8, 2, 1
                )
        );

        check(
                RuleCatalogue.scopeIds(
                        city, ScopeOption.VEHICLES_ON_ROAD)
                        .equals(List.of("R-1", "R-2")),
                "Road IDs not derived from map"
        );

        check(
                RuleCatalogue.scopeIds(
                        city, ScopeOption.LIGHTS_ON_ROAD)
                        .equals(List.of("R-1")),
                "Only incoming signal roads are valid"
        );

        check(
                RuleCatalogue.scopeIds(
                        city, ScopeOption.LIGHTS_AT_INTERSECTION)
                        .equals(List.of("J-1")),
                "Intersection IDs missing"
        );

        check(
                RuleCatalogue.scopeIds(
                        city, ScopeOption.ALL).isEmpty(),
                "ALL does not take an ID"
        );

        check(
                !ScopeOption.ALL.needsId(),
                "ALL should not request ID"
        );

        check(
                ScopeOption.ROAD_BY_ID.needsId(),
                "Selected road requires ID"
        );
    }

    private static void testDraftAndImmutability() {

        RuleDraft draft = new RuleDraft(
                "Rain congestion cap",
                TargetCategory.VEHICLE,
                ScopeOption.ALL,
                null,

                new RuleDraft.ConditionDraft(
                        ConditionOption.VEHICLE_COUNT_ON_ROAD,
                        RuleOperator.GREATER_THAN,
                        "10",
                        false
                ),

                new RuleDraft.ConditionDraft(
                        ConditionOption.WEATHER,
                        RuleOperator.EQUAL,
                        "RAIN",
                        false
                ),

                RuleDraft.Connector.AND,

                new RuleDraft.ActionDraft(
                        ActionOption.LIMIT_SPEED,
                        "0.8"
                ),

                "5",
                true
        );

        check(
                draft.secondCondition() != null,
                "Second condition lost"
        );

        check(
                draft.connector() == RuleDraft.Connector.AND,
                "Connector lost"
        );

        check(
                draft.enabled(),
                "Enabled flag lost"
        );

        check(
                draft.action().rawValue().equals("0.8"),
                "Action parameter lost"
        );

        try {
            RuleCatalogue.actionsFor(
                    TargetCategory.VEHICLE).clear();

            throw new AssertionError(
                    "Catalogue must be read-only"
            );

        } catch (UnsupportedOperationException expected) {
            checks++;
        }
    }
}