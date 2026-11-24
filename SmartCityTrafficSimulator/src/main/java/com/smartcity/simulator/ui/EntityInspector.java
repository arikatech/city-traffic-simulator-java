package com.smartcity.simulator.ui;

import com.smartcity.simulator.simulation.view.InspectionSnapshot;
import java.awt.*;
import java.util.Objects;
import javax.swing.*;

/** A purely read-only Swing inspector. No domain objects or engine mutation. */
public final class EntityInspector extends JPanel {
    private final JLabel title = new JLabel("Select an object on the map");
    private final JLabel subtitle = new JLabel("Click a vehicle, signal, pedestrian or road.");
    private final JPanel properties = new JPanel(new GridBagLayout());
    private final JButton clear = new JButton("Clear selection");
    private Runnable clearAction = () -> {};

    public EntityInspector() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        subtitle.setForeground(new Color(115, 135, 147));
        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 4));
        row.add(clear);
        header.add(row, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        JPanel aligned = new JPanel(new BorderLayout());
        aligned.add(properties, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(aligned);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        clear.addActionListener(e -> clearAction.run());
        showInspection(InspectionSnapshot.message("No selection", "Click an object in the map to inspect it."));
    }

    public void onClear(Runnable action) { clearAction = Objects.requireNonNull(action); }

    public void showInspection(InspectionSnapshot inspection) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Inspector updates must occur on Swing EDT");
        Objects.requireNonNull(inspection);
        title.setText(inspection.title());
        subtitle.setText("<html>" + escape(inspection.subtitle()) + "</html>");
        properties.removeAll();
        int i = 0;
        for (InspectionSnapshot.Field field : inspection.fields()) {
            GridBagConstraints labelPos = new GridBagConstraints();
            labelPos.gridx = 0; labelPos.gridy = i;
            labelPos.anchor = GridBagConstraints.NORTHWEST;
            labelPos.insets = new Insets(7, 2, 7, 10);
            JLabel key = new JLabel(field.label());
            key.setForeground(new Color(117, 147, 159));
            properties.add(key, labelPos);

            GridBagConstraints valuePos = new GridBagConstraints();
            valuePos.gridx = 1; valuePos.gridy = i;
            valuePos.weightx = 1; valuePos.fill = GridBagConstraints.HORIZONTAL;
            valuePos.anchor = GridBagConstraints.NORTHWEST;
            valuePos.insets = new Insets(7, 0, 7, 0);
            JLabel value = new JLabel("<html>" + escape(field.value()).replace("\n", "<br>") + "</html>");
            properties.add(value, valuePos);
            i++;
        }
        properties.revalidate();
        properties.repaint();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public String getTitleForTest() { return title.getText(); }
}
