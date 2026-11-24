package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.*;
import com.smartcity.simulator.routing.Route;
import com.smartcity.simulator.simulation.*;
import com.smartcity.simulator.ui.RuleManagementPanel;
import com.smartcity.simulator.vehicles.Car;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Runnable Stage 6.4 visual proof: create rules, pause, edit and observe effects. */
public final class Stage64RuleSimulationApp {

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Stage 6.4 Swing demo requires a desktop display.");
            return;
        }
        SwingUtilities.invokeLater(Stage64RuleSimulationApp::show);
    }

    private static void show() {
        CityMap city = new CityMap();
        Road first = new Road("R-1", 250, 10);
        Road next = new Road("R-2", 250, 10);
        city.addRoad(first);
        city.addRoad(next);
        city.connectRoads(first, next);
        var lane = first.addLane("L-1");
        next.addLane("L-2");
        city.addIntersection(new Intersection("J-1", List.of(first), 8, 2, 1));
        Car car = new Car("CAR-1", lane);
        car.assignRoute(new Route(List.of(first, next)));
        car.setSpeed(8);
        city.addVehicle(car);

        SimulationEngine engine = new SimulationEngine(new SimulationClock(1), city);
        RuleManagementPanel manager = new RuleManagementPanel(engine, city);

        JFrame window = new JFrame("Smart City — Stage 6.4 Rule Management");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLayout(new BorderLayout());
        JLabel metrics = new JLabel();
        Runnable repaintStats = () -> metrics.setText(String.format(
                "  State: %s | Tick: %d | Time: %.0fs | Car: %.1fm | Car speed cap: %.0f%% | R-2 entry: %s | Queued: %d",
                engine.getRunState(), engine.getTickCount(), engine.getElapsedSeconds(),
                car.getPositionMeters(),
                100.0 * engine.getLastResolvedRuleEffects().speedFractionFor("CAR-1"),
                next.isEntryOpen() ? "OPEN" : "CLOSED", engine.getPendingRuleCommandCount()));
        Timer ticker = new Timer(200, e -> {
            engine.step();
            manager.refreshFromEngine();
            repaintStats.run();
        });
        JButton start = new JButton("Start");
        JButton pause = new JButton("Pause");
        JButton step = new JButton("Step one tick");
        start.addActionListener(e -> {
            engine.startRunning();
            ticker.start();
            repaintStats.run();
        });
        pause.addActionListener(e -> {
            ticker.stop();
            engine.pauseRunning();
            // Apply queued commands immediately, without advancing time.
            engine.applyPendingRuleCommandsWhilePaused();
            manager.refreshFromEngine();
            repaintStats.run();
        });
        step.addActionListener(e -> {
            ticker.stop();
            engine.pauseRunning();
            engine.step();
            manager.refreshFromEngine();
            repaintStats.run();
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(start);
        controls.add(pause);
        controls.add(step);
        controls.add(metrics);
        window.add(controls, BorderLayout.NORTH);
        window.add(manager, BorderLayout.CENTER);
        repaintStats.run();
        window.setSize(1350, 820);
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }
}
