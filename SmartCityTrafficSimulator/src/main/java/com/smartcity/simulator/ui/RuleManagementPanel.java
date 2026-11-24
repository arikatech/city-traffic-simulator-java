package com.smartcity.simulator.ui;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.rules.builder.CompiledRule;
import com.smartcity.simulator.rules.builder.RuleExtensionRegistry;
import com.smartcity.simulator.rules.builder.DefaultRuleExtensions;
import com.smartcity.simulator.rules.builder.RuleDraft;
import com.smartcity.simulator.rules.management.RuleCommand;
import com.smartcity.simulator.rules.management.RuleCommandResult;
import com.smartcity.simulator.rules.management.RuleSummary;
import com.smartcity.simulator.simulation.SimulationEngine;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * Swing-only lifecycle UI. All callbacks, simulation steps and refresh calls
 * must run on the EDT (the Stage 6 baseline uses a javax.swing.Timer).
 * This class never directly mutates a RuleEngine or a simulation entity.
 */
public final class RuleManagementPanel extends JPanel {

    private final SimulationEngine engine;
    private final RuleBuilderPanel builderPanel;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel status = new JLabel("Ready to create rules.");
    private List<RuleSummary> displayed = List.of();

    public RuleManagementPanel(SimulationEngine engine, CityMap city) {
        this(engine, city, () -> "USER-" + UUID.randomUUID());
    }

    public RuleManagementPanel(
            SimulationEngine engine, CityMap city, Supplier<String> idSupplier) {
        this(engine, city, idSupplier, DefaultRuleExtensions.create());
    }

    public RuleManagementPanel(
            SimulationEngine engine, CityMap city, Supplier<String> idSupplier,
            RuleExtensionRegistry extensions) {
        super(new BorderLayout(8, 8));
        this.engine = Objects.requireNonNull(engine);
        this.builderPanel = new RuleBuilderPanel(
                Objects.requireNonNull(city), Objects.requireNonNull(idSupplier),
                Objects.requireNonNull(extensions), this::preparedRule);

        String[] columns = {"Name", "Target", "Scope", "Expression", "Action",
                "Priority", "Enabled", "ID"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(25);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {170, 100, 180, 290, 180, 75, 75, 165};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setBorder(BorderFactory.createTitledBorder("Registered rules"));
        right.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton edit = new JButton("Edit selected");
        JButton toggle = new JButton("Enable / disable");
        JButton delete = new JButton("Delete selected");
        JButton newRule = new JButton("New rule");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(edit);
        buttons.add(toggle);
        buttons.add(delete);
        buttons.add(newRule);
        right.add(buttons, BorderLayout.SOUTH);

        edit.addActionListener(e -> editSelected());
        toggle.addActionListener(e -> toggleSelected());
        delete.addActionListener(e -> deleteSelected());
        newRule.addActionListener(e -> builderPanel.clearForm());

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, builderPanel, right);
        split.setResizeWeight(0.43);
        add(split, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(status, BorderLayout.SOUTH);
        refreshFromEngine();
    }

    public RuleBuilderPanel getBuilderPanel() { return builderPanel; }
    public JTable getRuleTable() { return table; }
    public String getStatusText() { return status.getText(); }

    private void preparedRule(String id, RuleDraft draft, CompiledRule compiled) {
        RuleCommand command = builderPanel.isEditing()
                ? new RuleCommand.Replace(id, draft, compiled)
                : new RuleCommand.Add(id, draft, compiled);
        enqueue(command);
    }

    private void enqueue(RuleCommand command) {
        engine.enqueueRuleCommand(command);
        if (engine.getRunState() != SimulationEngine.RunState.RUNNING) {
            // Administrative boundary: does not advance simulated time.
            engine.applyPendingRuleCommandsWhilePaused();
        }
        refreshFromEngine();
        if (engine.getPendingRuleCommandCount() > 0) {
            status.setText("Queued " + command.getClass().getSimpleName()
                    + " for the next simulation tick.");
        }
        // The RuleBuilderPanel writes its own provisional feedback after its
        // callback returns, so defer replacing it until this event completes.
        String latest = status.getText();
        SwingUtilities.invokeLater(() -> builderPanel.showLifecycleStatus(
                latest, !latest.startsWith("Rejected")));
    }

    private RuleSummary selectedRule() {
        int row = table.getSelectedRow();
        if (row < 0) {
            status.setText("Select a registered rule first.");
            return null;
        }
        int modelRow = table.convertRowIndexToModel(row);
        return displayed.get(modelRow);
    }

    public void editSelected() {
        RuleSummary rule = selectedRule();
        if (rule == null) return;
        RuleDraft draft = engine.getManagedRuleDraft(rule.id());
        if (draft == null) {
            status.setText("This Java-defined rule has no editable visual draft.");
            return;
        }
        builderPanel.loadForEditing(rule.id(), draft);
        status.setText("Editing " + rule.name() + ". Validate to replace at the next boundary.");
    }

    public void toggleSelected() {
        RuleSummary rule = selectedRule();
        if (rule != null) enqueue(new RuleCommand.SetEnabled(rule.id(), !rule.enabled()));
    }

    public void deleteSelected() {
        RuleSummary rule = selectedRule();
        if (rule != null) {
            enqueue(new RuleCommand.Delete(rule.id()));
            builderPanel.clearForm();
        }
    }

    /** Call after every GUI-driven step to consume acknowledgements and redraw rows. */
    public void refreshFromEngine() {
        List<RuleCommandResult> acknowledgements = engine.drainRuleCommandResults();
        if (!acknowledgements.isEmpty()) {
            RuleCommandResult last = acknowledgements.get(acknowledgements.size() - 1);
            status.setText(last.success() ? last.message() : "Rejected: " + last.message());
            builderPanel.showLifecycleStatus(status.getText(), last.success());
        }
        String selectedId = null;
        int selected = table.getSelectedRow();
        if (selected >= 0 && table.convertRowIndexToModel(selected) < displayed.size()) {
            selectedId = displayed.get(table.convertRowIndexToModel(selected)).id();
        }
        displayed = engine.getRuleSummaries();
        tableModel.setRowCount(0);
        for (RuleSummary rule : displayed) {
            tableModel.addRow(new Object[]{rule.name(), rule.target(), rule.scope(),
                    rule.expression(), rule.action(), rule.priority(), rule.enabled(), rule.id()});
        }
        if (selectedId != null) {
            for (int i = 0; i < displayed.size(); i++) {
                if (displayed.get(i).id().equals(selectedId)) {
                    int row = table.convertRowIndexToView(i);
                    if (row >= 0) table.setRowSelectionInterval(row, row);
                    break;
                }
            }
        }
    }
}
