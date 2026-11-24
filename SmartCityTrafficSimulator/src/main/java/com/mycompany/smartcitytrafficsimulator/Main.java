package com.smartcity.simulator;

import com.smartcity.simulator.ui.MainWindow;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Official entry point for the Smart City Traffic Simulator desktop application.
 *
 * Main only starts the Swing UI. MainWindow creates the selected scenario,
 * and the existing SimulationEngine owns the logical simulation.
 */

public final class Main {

    private Main() {
        // Utility class: do not instantiate.
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Smart City Traffic Simulator requires a graphical desktop.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception exception) {
                // Use Swing's built-in default if the system theme is unavailable.
                System.err.println("Using default Swing appearance: "
                        + exception.getMessage());
            }

            new MainWindow().setVisible(true);
        });
   
   
    }
     
}