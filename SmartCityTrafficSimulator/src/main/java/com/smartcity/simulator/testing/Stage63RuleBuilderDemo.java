package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.city.Intersection;
import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.ui.RuleBuilderPanel;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Standalone Swing demonstration: prepares but does NOT register rules. */
public final class Stage63RuleBuilderDemo {
    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("A graphical desktop is required for the Swing demo.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            CityMap city = new CityMap();
            Road r1 = new Road("R-1", 100, 15);
            Road r2 = new Road("R-2", 140, 12);
            city.addRoad(r1);
            city.addRoad(r2);
            city.addIntersection(new Intersection("J-1", List.of(r1), 8, 2, 1));

            JTextArea prepared = new JTextArea(6, 36);
            prepared.setEditable(false);
            prepared.setText("Prepared rules (not registered or applied):\n");
            RuleBuilderPanel panel = new RuleBuilderPanel(city,
                    (id, draft, compiled) -> prepared.append(
                            id + " | " + draft.name() + " | " + draft.target()
                            + " | Priority " + draft.priority()
                            + " | " + (draft.enabled() ? "Enabled" : "Disabled")
                            + " | " + compiled.getClass().getSimpleName() + "\n"));

            JFrame frame = new JFrame("Smart City - Stage 6.3 Rule Builder Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.add(panel, BorderLayout.CENTER);
            frame.add(new JScrollPane(prepared), BorderLayout.SOUTH);
            frame.setSize(650, 850);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
