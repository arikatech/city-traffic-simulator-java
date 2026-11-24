package com.smartcity.simulator.ui;

import com.smartcity.simulator.city.Road;
import com.smartcity.simulator.events.SpeedLimitChangeEvent;
import com.smartcity.simulator.ui.desktop.DesktopSession;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * User-facing editor for scheduling a SpeedLimitChangeEvent on the active session.
 * Event execution still belongs entirely to SimulationEngine/EventScheduler.
 */
public final class EventSchedulingPanel extends JPanel {
    private static final double EPSILON = 1e-9;

    private final JComboBox<Road> roads = new JComboBox<>();
    private final JTextField eventTime = new JTextField("2.0", 8);
    private final JTextField newLimit = new JTextField("8.0", 8);
    private final JButton schedule = new JButton("Schedule speed-limit change");
    private final JLabel feedback = new JLabel("Choose a road, time and new speed limit.");
    private final JLabel currentSpeed = new JLabel(" ");
    private final JLabel currentTime = new JLabel(" ");
    private final DefaultListModel<String> eventRows = new DefaultListModel<>();
    private final List<ScheduledSpeed> scheduled = new ArrayList<>();
    private DesktopSession session;
    private int nextId = 1;

    private record ScheduledSpeed(SpeedLimitChangeEvent event, Road road, double value) {}

    public EventSchedulingPanel() {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Schedule a speed-limit event");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 2, 5, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;

        addFormRow(form, c, 0, "Road", roads);
        addFormRow(form, c, 1, "Trigger time (simulation seconds)", eventTime);
        addFormRow(form, c, 2, "New base speed limit (m/s)", newLimit);
        c.gridy = 6;
        form.add(schedule, c);
        c.gridy = 7;
        form.add(currentSpeed, c);
        c.gridy = 8;
        form.add(currentTime, c);
        c.gridy = 9;
        form.add(feedback, c);

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.add(title, BorderLayout.NORTH);
        header.add(form, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JList<String> list = new JList<>(eventRows);
        JPanel history = new JPanel(new BorderLayout(0, 5));
        history.add(new JLabel("Events scheduled from this panel"), BorderLayout.NORTH);
        history.add(new JScrollPane(list), BorderLayout.CENTER);
        add(history, BorderLayout.CENTER);

        roads.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        value instanceof Road road ? road.getId() : value,
                        index, selected, focus);
            }
        });
        roads.addActionListener(e -> updateCurrentSpeed());
        schedule.addActionListener(e -> scheduleFromForm());
    }

    private static void addFormRow(JPanel panel, GridBagConstraints c,
            int index, String label, java.awt.Component input) {
        c.gridy = index * 2;
        panel.add(new JLabel(label), c);
        c.gridy++;
        panel.add(input, c);
    }

    /** Rebinds the editor to the fresh session created after selecting/resetting a scenario. */
    public void bindSession(DesktopSession session) {
        requireEdt();
        this.session = Objects.requireNonNull(session, "session");
        nextId = 1;
        scheduled.clear();
        eventRows.clear();
        roads.removeAllItems();
        for (Road road : session.city().getRoads()) {
            roads.addItem(road);
        }
        eventTime.setText(String.format(Locale.ROOT, "%.1f", session.elapsedSeconds() + 2.0));
        feedback.setForeground(Color.DARK_GRAY);
//        feedback.setText(session.scenario() == com.smartcity.simulator.scenarios.ScenarioPreset.SLIPPERY
//                ? "SPEED_ZONE preset: R-4 changes to 8 m/s at t=2 s."
//                : "Enter an event time and press Schedule.");
        refreshFromSimulation();
    }

    /** Schedules a real domain event; callable independently of the form for verification. */
    public SpeedLimitChangeEvent scheduleSpeedChange(Road road, double timeSeconds, double limit) {
        requireEdt();
        if (session == null) throw new IllegalStateException("No simulation session");
        if (road == null || !session.city().getRoads().contains(road)) {
            throw new IllegalArgumentException("Select a road from the current city");
        }
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0
                || timeSeconds + EPSILON < session.elapsedSeconds()) {
            throw new IllegalArgumentException("Event time must be at or after current simulation time");
        }
        SpeedLimitChangeEvent event = new SpeedLimitChangeEvent(
                "USER-SPEED-" + nextId, timeSeconds, road, limit);
        // No direct onStart() call: the EventScheduler will activate it when due.
        session.engine().scheduleEvent(event);
        nextId++;
        scheduled.add(new ScheduledSpeed(event, road, limit));
        refreshFromSimulation();
        return event;
    }

    private void scheduleFromForm() {
        requireEdt();
        try {
            Road road = (Road) roads.getSelectedItem();
            double time = Double.parseDouble(eventTime.getText().trim());
            double limit = Double.parseDouble(newLimit.getText().trim());
            SpeedLimitChangeEvent event = scheduleSpeedChange(road, time, limit);
            feedback.setForeground(new Color(0, 110, 50));
            feedback.setText("Scheduled " + event.getId() + " for " + road.getId());
        } catch (NumberFormatException exception) {
            feedback.setForeground(new Color(170, 35, 35));
            feedback.setText("Time and speed must be valid numbers.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            feedback.setForeground(new Color(170, 35, 35));
            feedback.setText(exception.getMessage());
        }
    }

    /** Refreshes status and road speed after logical simulation steps. */
    public void refreshFromSimulation() {
        requireEdt();
        if (session == null) return;
        currentTime.setText(String.format(Locale.ROOT,
                "Current simulation time: %.1f s", session.elapsedSeconds()));
        updateCurrentSpeed();
        eventRows.clear();
        for (ScheduledSpeed row : scheduled) {
            eventRows.addElement(String.format(Locale.ROOT,
                    "%s | %s → %.1f m/s | t=%.1f s | %s",
                    row.event().getId(), row.road().getId(), row.value(),
                    row.event().getStartSeconds(), row.event().getStatus()));
        }
    }

    private void updateCurrentSpeed() {
        Road road = (Road) roads.getSelectedItem();
        if (road != null) {
            currentSpeed.setText(String.format(Locale.ROOT,
                    "%s current limit: %.1f m/s", road.getId(), road.getBaseSpeedLimit()));
        } else {
            currentSpeed.setText("No road selected");
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Event panel must be used on the Swing EDT");
        }
    }
}
