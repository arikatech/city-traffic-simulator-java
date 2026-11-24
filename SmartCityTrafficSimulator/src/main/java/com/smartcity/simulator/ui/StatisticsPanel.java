package com.smartcity.simulator.ui;

import com.smartcity.simulator.statistics.StatisticsSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Read-only, compact presentation of committed simulation statistics. */
public final class StatisticsPanel extends JPanel {
    private static final Color BACKGROUND = new Color(237, 243, 244);
    private static final Color TILE = new Color(250, 252, 252);
    private static final Color ACCENT = new Color(26, 74, 92);

    private final JLabel[] values = new JLabel[6];

    public StatisticsPanel() {
        super(new BorderLayout(0, 5));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(5, 8, 7, 8));
        setPreferredSize(new Dimension(500, 92));
        JLabel title = new JLabel("LIVE STATISTICS  /  committed ticks only");
        title.setForeground(new Color(74, 94, 104));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        add(title, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 6, 6, 0));
        cards.setOpaque(false);
        String[] labels = {
            "COMPLETED", "MEAN TRIP", "WAITING", "QUEUE",
            "CROSSINGS", "EMERGENCY MEAN"
        };
        for (int i = 0; i < labels.length; i++) {
            JPanel cell = new JPanel(new BorderLayout(0, 2));
            cell.setBackground(TILE);
            cell.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(207, 217, 222)),
                    BorderFactory.createEmptyBorder(3, 3, 3, 3)));
            JLabel heading = new JLabel(labels[i], SwingConstants.CENTER);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 9f));
            heading.setForeground(new Color(91, 102, 111));
            JLabel value = new JLabel("—", SwingConstants.CENTER);
            value.setFont(value.getFont().deriveFont(Font.BOLD, 17f));
            value.setForeground(ACCENT);
            values[i] = value;
            cell.add(heading, BorderLayout.NORTH);
            cell.add(value, BorderLayout.CENTER);
            cards.add(cell);
        }
        add(cards, BorderLayout.CENTER);
    }

    public void showStatistics(StatisticsSnapshot stats) {
        if (stats == null) throw new IllegalArgumentException("Statistics required");
        values[0].setText(Integer.toString(stats.completedTrips()));
        values[1].setText(formatMean(stats.meanTravelTimeSeconds()));
        values[2].setText(String.format(Locale.ROOT, "%.1f s", stats.cumulativeWaitingSeconds()));
        values[3].setText(Integer.toString(stats.currentQueueLength()));
        values[4].setText(Integer.toString(stats.successfulPedestrianCrossings()));
        values[5].setText(formatMean(stats.meanEmergencyTripTimeSeconds()));
        values[5].setToolTipText("Completed emergency journeys: " + stats.completedEmergencyTrips());
        values[2].setToolTipText(String.format(Locale.ROOT,
                "Vehicle waiting %.1f s + pedestrian waiting %.1f s",
                stats.vehicleWaitingSeconds(), stats.pedestrianWaitingSeconds()));
        values[3].setToolTipText("Vehicles with no forward progress during the last committed tick");
    }

    private static String formatMean(OptionalDouble value) {
        return value.isPresent() ? String.format(Locale.ROOT, "%.1f s", value.getAsDouble())
                : "No trips";
    }

    /** Read-only test hook. */
    public String valueForTest(int index) { return values[index].getText(); }
}
