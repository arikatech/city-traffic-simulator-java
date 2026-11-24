package com.smartcity.simulator.ui;

import com.smartcity.simulator.scenarios.ScenarioPreset;
import com.smartcity.simulator.scenarios.ScenarioFactory;
import com.smartcity.simulator.simulation.view.MapSnapshot;
import com.smartcity.simulator.simulation.view.InspectionSnapshot;
import com.smartcity.simulator.simulation.view.InspectionSnapshotFactory;
import com.smartcity.simulator.ui.map.MapSelection;
import com.smartcity.simulator.ui.desktop.DesktopSession;
import com.smartcity.simulator.ui.desktop.DisplayTickBudget;
import com.smartcity.simulator.ui.desktop.SpawnKind;
import com.smartcity.simulator.ui.desktop.Stage6RuleDock;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Stage 7.2 desktop controller/view. Engine and Swing run on the single EDT.
 * paintComponent() NEVER invokes a logical step; only controls/timer do.
 */
public final class MainWindow extends JFrame {
    private static final int FRAME_INTERVAL_MS = 40;
    private static final int MAX_STEPS_PER_FRAME = 12;
    private final Function<ScenarioPreset, DesktopSession> newSession;
    private final CityMapPanel mapPanel = new CityMapPanel();
    private final StatisticsPanel statisticsPanel = new StatisticsPanel();
    private final JButton start = new JButton("▶ Start");
    private final JButton pause = new JButton("Ⅱ Pause");
    private final JButton singleStep = new JButton("→ Single step");
    private final JButton reset = new JButton("↺ Reset");
    private final JComboBox<ScenarioPreset> scenarioPicker =
            new JComboBox<>(ScenarioPreset.values());
    private final JComboBox<SpeedChoice> displaySpeed =
            new JComboBox<>(SpeedChoice.values());
    private final JComboBox<SpawnKind> spawnKind = new JComboBox<>();
    private final JButton spawn = new JButton("+ Spawn");
    private final JLabel status = new JLabel(" ");
    private final JLabel feedback = new JLabel(" Ready — press Start or Single step.");
    private final JScrollPane rulePanelContainer = new JScrollPane();
    private final EntityInspector inspector = new EntityInspector();
    private final EventSchedulingPanel eventPanel = new EventSchedulingPanel();
    private final JTabbedPane rightTabs = new JTabbedPane();
    private MapSelection selection;
    private final Timer timer;

    private DesktopSession session;
    private DisplayTickBudget tickBudget;
    private boolean running;
    private long lastWallNanos;

    public MainWindow() {
        this(preset -> new ScenarioFactory().create(preset));
    }

    /** Testable injection point; permits different scenarios without GUI-domain coupling. */
    public MainWindow(Function<ScenarioPreset, DesktopSession> factory) {
        super("Smart City Traffic Simulator — Stage 7 (Complete)");
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Construct MainWindow on Swing EDT");
        }
        newSession = Objects.requireNonNull(factory, "factory");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        setMinimumSize(new Dimension(900, 600));
        setSize(1450, 860);

        add(toolbar(), BorderLayout.NORTH);
        // The original full-height Stage 6 rule editor remains unchanged.
        // Inspector uses a separate tab, never compressing the rule form.
        rightTabs.addTab("Rule Builder", rulePanelContainer);
        rightTabs.addTab("Inspector", inspector);
        rightTabs.addTab("Events", eventPanel);
        // Keep statistics below the map only: the Rule Builder and Inspector
        // retain their full vertical space and original layouts.
        JPanel left = new JPanel(new BorderLayout(0, 0));
        left.add(mapPanel, BorderLayout.CENTER);
        left.add(statisticsPanel, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                left, rightTabs);
        split.setResizeWeight(0.67);
        split.setContinuousLayout(true);
        split.setDividerSize(7);
        rulePanelContainer.setMinimumSize(new Dimension(310, 200));
        rulePanelContainer.getVerticalScrollBar().setUnitIncrement(16);
        add(split, BorderLayout.CENTER);
        // Status now lives in the dark toolbar; no tall white bottom strip.

        timer = new Timer(FRAME_INTERVAL_MS, e -> onTimer());
        timer.setCoalesce(true);
        wireControls();
        inspector.onClear(this::clearSelection);
        mapPanel.onSelection(picked -> {
            selection = picked;
            if (picked == null) {
                inspector.showInspection(InspectionSnapshot.message(
                        "No selection", "Click any map object to inspect it."));
            } else {
                refreshInspector();
                rightTabs.setSelectedComponent(inspector);
            }
        });
        resetScenario((ScenarioPreset) scenarioPicker.getSelectedItem());
        setLocationRelativeTo(null);
    }

    private JPanel toolbar() {
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        top.setBackground(new Color(32, 50, 62));
        JLabel heading = new JLabel("SMART CITY  /  LIVE SIMULATION");
        heading.setForeground(Color.WHITE);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        top.add(heading, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 7, 2));
        controls.add(start);
        controls.add(pause);
        controls.add(singleStep);
        controls.add(reset);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(new JLabel("Scenario:"));
        controls.add(scenarioPicker);
        controls.add(new JLabel("Display:"));
        displaySpeed.setSelectedItem(SpeedChoice.X1);
        controls.add(displaySpeed);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(new JLabel("Spawn:"));
        controls.add(spawnKind);
        controls.add(spawn);
        top.add(controls, BorderLayout.CENTER);
        status.setForeground(new Color(205, 225, 235));
        status.setFont(status.getFont().deriveFont(11f));
        top.add(status, BorderLayout.SOUTH);
        return top;
    }

    private void wireControls() {
        start.addActionListener(e -> startSimulation());
        pause.addActionListener(e -> pauseSimulation());
        singleStep.addActionListener(e -> singleStep());
        reset.addActionListener(e -> resetScenario(selectedScenario()));
        scenarioPicker.addActionListener(e -> {
            if (session != null && selectedScenario() != session.scenario()) {
                resetScenario(selectedScenario());
            }
        });
        spawn.addActionListener(e -> spawnSelected());
    }

    private ScenarioPreset selectedScenario() {
        return (ScenarioPreset) Objects.requireNonNull(scenarioPicker.getSelectedItem());
    }

    private SpeedChoice selectedSpeed() {
        return (SpeedChoice) Objects.requireNonNull(displaySpeed.getSelectedItem());
    }

    public void startSimulation() {
        requireEdt();
        if (running) return;
        running = true;
        tickBudget.reset();
        lastWallNanos = System.nanoTime();
        timer.start();
        updateButtonStates();
        feedback.setText(" Running logical ticks at " + selectedSpeed() + " display speed.");
    }

    public void pauseSimulation() {
        requireEdt();
        running = false;
        timer.stop();
        tickBudget.reset();
        updateButtonStates();
        feedback.setText(" Paused. Logical simulation time is frozen.");
    }

    /** Exactly one engine.step(), regardless of display-speed selection. */
    public void singleStep() {
        requireEdt();
        pauseSimulation();
        try {
            session.step();
            refreshFromCommittedState();
            feedback.setText(" Advanced exactly one logical step (dt="
                    + session.fixedStepSeconds() + " s).");
        } catch (RuntimeException problem) {
            showFailure("Single step failed", problem);
        }
    }

    /** Rebuilds CityMap, SimulationClock, SimulationEngine, events, spawner and rule UI. */
    public void resetScenario(ScenarioPreset preset) {
        requireEdt();
        Objects.requireNonNull(preset, "preset");
        running = false;
        timer.stop();
        try {
            DesktopSession replacement = Objects.requireNonNull(newSession.apply(preset));
            // Build the Stage 6 panel for the NEW engine; never keep old command targets.
            var newRulesPanel = Stage6RuleDock.create(replacement.city(), replacement.engine());
            session = replacement;
            eventPanel.bindSession(replacement);
            clearSelection();
            tickBudget = new DisplayTickBudget(session.fixedStepSeconds(),
                    MAX_STEPS_PER_FRAME);
            rulePanelContainer.setViewportView(newRulesPanel);
            spawnKind.removeAllItems();
            for (SpawnKind type : session.supportedSpawnTypes()) spawnKind.addItem(type);
            if (scenarioPicker.getSelectedItem() != preset) scenarioPicker.setSelectedItem(preset);
            refreshFromCommittedState();
            updateButtonStates();
            feedback.setText(" Reset " + preset + " with deterministic seed "
                    + ScenarioFactory.REPRODUCIBLE_SEED + ".");
        } catch (RuntimeException problem) {
            showFailure("Scenario initialization failed", problem);
            if (session == null) throw problem;
        }
    }

    private void spawnSelected() {
        requireEdt();
        SpawnKind kind = (SpawnKind) spawnKind.getSelectedItem();
        if (kind == null) return;
        try {
            String result = session.spawn(kind);
            refreshFromCommittedState();
            feedback.setText(" " + result);
        } catch (RuntimeException problem) {
            showFailure("Spawn failed", problem);
        }
    }

    private void onTimer() {
        if (!running) return;
        long now = System.nanoTime();
        double wallSeconds = Math.max(0.0, (now - lastWallNanos) / 1_000_000_000.0);
        lastWallNanos = now;
        int steps = tickBudget.claim(wallSeconds, selectedSpeed().factor);
        if (steps == 0) return;
        try {
            for (int i = 0; i < steps; i++) session.step();
            refreshFromCommittedState();
        } catch (RuntimeException problem) {
            pauseSimulation();
            showFailure("Simulation stopped after a failed tick", problem);
        }
    }

    private void refreshFromCommittedState() {
        MapSnapshot map = session.publishMap();
        mapPanel.setSnapshot(map);
        statisticsPanel.showStatistics(session.engine().getStatisticsSnapshot());
        eventPanel.refreshFromSimulation();
        var decisions = session.engine().getLastVehicleRuleObservations();
        Set<String> matched = decisions.entrySet().stream()
                .filter(e -> !e.getValue().matchedRuleIds().isEmpty())
                .map(java.util.Map.Entry::getKey).collect(Collectors.toSet());
        Set<String> capped = decisions.entrySet().stream()
                .filter(e -> e.getValue().permittedCapMetersPerSecond()
                        + 0.000001 < e.getValue().baselineCapMetersPerSecond())
                .map(java.util.Map.Entry::getKey).collect(Collectors.toSet());
        mapPanel.setRuleHighlights(matched, capped);
        refreshInspector();
        long closed = map.roads().stream().filter(MapSnapshot.RoadView::closed).count();
        status.setText(String.format(java.util.Locale.ROOT,
                "  %s  |  Logical time: %.1f s  |  Tick: %d  |  Weather: %s"
                + "  |  Vehicles: %d  |  Pedestrians: %d"
                + "  |  Entry-closed roads: %d  |  Incidents: %d",
                running ? "RUNNING" : "PAUSED / READY", session.elapsedSeconds(),
                session.tickCount(), session.weatherLabel(), map.vehicles().size(),
                map.pedestrians().size(), closed, map.incidents().size()));
    }

    private void clearSelection() {
        selection = null;
        mapPanel.setSelection(null);
        inspector.showInspection(InspectionSnapshot.message(
                "No selection", "Click a vehicle, signal, pedestrian or road to inspect it."));
    }

    private void refreshInspector() {
        if (session == null || selection == null) return;
        InspectionSnapshot data = InspectionSnapshotFactory.capture(
                session.city(), session.engine(), mapPanel.getSnapshot(), selection);
        if (data == null) {
            String endedId = selection.id();
            mapPanel.setSelection(null);
            selection = null;
            inspector.showInspection(InspectionSnapshot.message("No longer present",
                    "Object " + endedId + " has left the map or its incident has expired."));
            return;
        }
        inspector.showInspection(data);
    }

    /** Test hooks do not advance the simulation. */
    public String getInspectorTitleForTest() { return inspector.getTitleForTest(); }
    public void selectForTest(MapSelection selected) {
        requireEdt();
        selection = selected;
        mapPanel.setSelection(selected);
        refreshInspector();
    }

    private void updateButtonStates() {
        start.setEnabled(!running);
        pause.setEnabled(running);
        singleStep.setEnabled(!running);
    }

    private void showFailure(String title, RuntimeException problem) {
        feedback.setText(" " + title + ": " + problem.getMessage());
        JOptionPane.showMessageDialog(this, problem.getMessage(),
                title, JOptionPane.ERROR_MESSAGE);
    }

    public double getSimulationTimeForTest() { return session.elapsedSeconds(); }
    public long getTickCountForTest() { return session.tickCount(); }
    public boolean isRunningForTest() { return running; }
    public MapSnapshot getMapForTest() { return mapPanel.getSnapshot(); }
    public String getStatusTextForTest() { return status.getText(); }
    public String getStatisticForTest(int index) { return statisticsPanel.valueForTest(index); }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("UI commands must execute on the Swing EDT");
        }
    }

    private enum SpeedChoice {
        HALF("0.5×", 0.5), X1("1×", 1.0), X2("2×", 2.0),
        X4("4×", 4.0), X8("8×", 8.0);
        private final String text;
        private final double factor;
        SpeedChoice(String text, double factor) { this.text = text; this.factor = factor; }
        @Override public String toString() { return text; }
    }
}