package com.smartcity.simulator.testing;

import com.smartcity.simulator.ui.MainWindow;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Stage 7.2 desktop entry point. Run File in NetBeans. */
public final class Stage72DesktopApp {
    private Stage72DesktopApp() { }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("A graphical desktop is required for Stage 7.2.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Swing's standard L&F remains available.
            }
            new MainWindow().setVisible(true);
        });
    }
}
