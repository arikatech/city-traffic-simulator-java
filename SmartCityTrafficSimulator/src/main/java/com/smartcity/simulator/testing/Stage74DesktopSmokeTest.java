package com.smartcity.simulator.testing;

import com.smartcity.simulator.scenarios.ScenarioPreset;
import com.smartcity.simulator.ui.MainWindow;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** Optional graphical integration check. Run on a desktop, NOT headless. */
public final class Stage74DesktopSmokeTest {
    private Stage74DesktopSmokeTest() { }
    private static int checks;
    private static void check(boolean ok, String text) {
        if (!ok) throw new AssertionError(text);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("STAGE 7.4 GUI TEST SKIPPED (graphical desktop required)");
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            try {
                check(window.getTickCountForTest() == 0, "Fresh simulation starts at tick zero");
                check("0".equals(window.getStatisticForTest(0)), "Empty stats at launch");
                check("No trips".equals(window.getStatisticForTest(1)), "No fake initial mean");
                check("No trips".equals(window.getStatisticForTest(5)), "No fake emergency mean");

                window.singleStep();
                check(window.getTickCountForTest() == 1, "Single Step commits exactly one tick");
                double after = window.getSimulationTimeForTest();
                check(after > 0, "Step advances logical time");
                long tick = window.getTickCountForTest();
                var image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
                window.setSize(1200, 800);
                var graphics = image.createGraphics();
                try { window.paint(graphics); } finally { graphics.dispose(); }
                check(window.getTickCountForTest() == tick,
                        "Painting the complete window never advances simulation");

                window.startSimulation();
                check(window.isRunningForTest(), "Start enables timer");
                window.pauseSimulation();
                check(!window.isRunningForTest(), "Pause disables timer");
                check(window.getTickCountForTest() == tick,
                        "No queued timer callback advances after immediate pause");

                window.resetScenario(ScenarioPreset.values()[0]);
                check(window.getTickCountForTest() == 0, "Reset rebuilds logical clock");
                check("0".equals(window.getStatisticForTest(0)), "Reset clears completed trips");
                check("0.0 s".equals(window.getStatisticForTest(2)), "Reset clears waiting");
                check("0".equals(window.getStatisticForTest(3)), "Reset clears queue");
                check("0".equals(window.getStatisticForTest(4)), "Reset clears crossings");
                check(window.getStatusTextForTest().contains("Weather:"),
                        "Top status still shows weather");
                check(window.getStatusTextForTest().contains("Incidents:"),
                        "Top status still shows incidents");
            } finally {
                window.pauseSimulation();
                window.dispose();
            }
        });
        System.out.println("STAGE 7.4 GUI SMOKE TEST PASSED! (" + checks + " checks)");
    }
}
