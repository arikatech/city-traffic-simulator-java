package com.smartcity.simulator.ui.desktop;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.simulation.SimulationEngine;
import com.smartcity.simulator.rules.management.RuleCommand;
import com.smartcity.simulator.ui.RuleBuilderPanel;
import java.awt.BorderLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Small bootstrap adapter, intentionally outside the engine/model.
 * Prefers the entire existing Stage 6.4 RuleManagementPanel (active list + edits).
 * Its constructor was not part of the Stage 7.1 artifact. Match supported
 * constructors at launch instead of guessing a Java signature at compile time.
 * Fallback is the actual Stage 6 RuleBuilderPanel and real Add commands.
 */
public final class Stage6RuleDock {
    private Stage6RuleDock() { }

    public static JComponent create(CityMap city, SimulationEngine engine) {
        String[] candidates = {
            "com.smartcity.simulator.ui.RuleManagementPanel",
            "com.smartcity.simulator.ui.rules.RuleManagementPanel",
            "com.smartcity.simulator.rules.management.RuleManagementPanel"
        };
        for (String candidate : candidates) {
            try {
                Class<?> type = Class.forName(candidate);
                if (!JComponent.class.isAssignableFrom(type)) continue;
                for (Constructor<?> ctor : type.getConstructors()) {
                    Class<?>[] args = ctor.getParameterTypes();
                    if (args.length != 2) continue;
                    if (args[0].isInstance(city) && args[1].isInstance(engine)) {
                        return (JComponent) ctor.newInstance(city, engine);
                    }
                    if (args[0].isInstance(engine) && args[1].isInstance(city)) {
                        return (JComponent) ctor.newInstance(engine, city);
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Try a different package name.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Stage 6 management panel failed: " + candidate, e);
            }
        }
        // Fully functional creation fallback; lifecycle list requires the Stage 6.4 panel.
        JPanel dock = new JPanel(new BorderLayout());
        JLabel notice = new JLabel("Rule builder connected; Stage 6.4 list needs adapter setup.");
        dock.add(notice, BorderLayout.NORTH);
        RuleBuilderPanel builder = new RuleBuilderPanel(city, (id, draft, compiled) -> {
            engine.enqueueRuleCommand(new RuleCommand.Add(id, draft, compiled));
            // Safe when paused: this is the same Stage 6 boundary, not direct rule mutation.
            engine.applyPendingRuleCommandsWhilePaused();
        });
        dock.add(builder, BorderLayout.CENTER);
        return dock;
    }
}
