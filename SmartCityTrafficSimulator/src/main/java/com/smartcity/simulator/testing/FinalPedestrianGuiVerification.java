package com.smartcity.simulator.testing;

import com.smartcity.simulator.ui.MainWindow;
import com.smartcity.simulator.ui.desktop.SpawnKind;
import com.smartcity.simulator.ui.map.MapSelection;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/** Optional end-to-end Swing verification. Run on desktop (or Xvfb on CI). */
public final class FinalPedestrianGuiVerification {
    private static int checks;
    private FinalPedestrianGuiVerification() { }
    private static void check(boolean ok, String detail) {
        checks++;
        if (!ok) throw new AssertionError(detail);
    }
    private static void collect(Component root, List<Component> all) {
        all.add(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, all);
        }
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("FINAL PEDESTRIAN GUI CHECK SKIPPED (desktop required)");
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            try {
                List<Component> components = new ArrayList<>();
                collect(window, components);
                JComboBox<?> spawnCombo = null;
                JButton spawn = null;
                for (Component component : components) {
                    if (component instanceof JComboBox<?> cb) {
                        for (int i = 0; i < cb.getItemCount(); i++) {
                            if (cb.getItemAt(i) == SpawnKind.PEDESTRIAN) spawnCombo = cb;
                        }
                    }
                    if (component instanceof JButton button
                            && button.getText().equals("+ Spawn")) spawn = button;
                }
                check(spawnCombo != null, "UI exposes pedestrian in Spawn dropdown");
                check(spawn != null, "UI has Spawn button");
                check(window.getMapForTest().pedestrians().isEmpty(), "No fabricated initial pedestrians");
                long initialTicks = window.getTickCountForTest();
                spawnCombo.setSelectedItem(SpawnKind.PEDESTRIAN);
                spawn.doClick();
                var pedestrians = window.getMapForTest().pedestrians();
                check(pedestrians.size() == 1, "Spawn button created a real visible pedestrian");
                check(window.getStatusTextForTest().contains("Pedestrians: 1"),
                        "Status displays live pedestrian count");
                check(window.getTickCountForTest() == initialTicks,
                        "Manual spawn did not advance logical time");
                window.selectForTest(new MapSelection(
                        MapSelection.Kind.PEDESTRIAN, pedestrians.get(0).id()));
                check(window.getInspectorTitleForTest().contains(pedestrians.get(0).id()),
                        "Selecting pedestrian updates inspector");
                window.singleStep();
                check(window.getTickCountForTest() == initialTicks + 1,
                        "Single Step is still exactly one dt");
                window.resetScenario(com.smartcity.simulator.scenarios.ScenarioPreset.NORMAL_TRAFFIC);
                check(window.getMapForTest().pedestrians().isEmpty(),
                        "Reset clears pedestrians");
                check(window.getTickCountForTest() == 0, "Reset clears logical time");
                check("0".equals(window.getStatisticForTest(4)),
                        "Reset clears crossing statistic");
            } finally {
                window.pauseSimulation();
                window.dispose();
            }
        });
        System.out.println("FINAL PEDESTRIAN GUI VERIFICATION PASSED! (" + checks + " checks)");
    }
}
