
package com.smartcity.simulator.ui;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.rules.builder.ActionChoice;
import com.smartcity.simulator.rules.builder.RuleExtensionRegistry;
import com.smartcity.simulator.rules.builder.CompiledRule;
import com.smartcity.simulator.rules.builder.RuleBuilder;
import com.smartcity.simulator.rules.builder.RuleCatalogue;
import com.smartcity.simulator.rules.builder.RuleDraft;
import com.smartcity.simulator.rules.builder.ScopeOption;
import com.smartcity.simulator.rules.builder.TargetCategory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;

/**
 * Visual editor for one/two-condition rules.
 * Validates and produces typed rules, but NEVER registers them with RuleEngine.
 * The submission handler is the boundary to the upcoming engine command layer.
 * Construct and interact with this Swing component on the Event Dispatch Thread.
 */
public final class RuleBuilderPanel extends JPanel {

    @FunctionalInterface
    public interface SubmissionHandler {
        void onPrepared(String id, RuleDraft draft, CompiledRule compiled);
    }

    private final CityMap city;
    private final RuleBuilder builder;
    private final RuleExtensionRegistry extensions;
    private final Supplier<String> newIdSupplier;
    private final SubmissionHandler handler;
    private String editingRuleId;

    private final JTextField nameField = new JTextField(22);
    private final JComboBox<TargetCategory> targetBox = new JComboBox<>(TargetCategory.values());
    private final JComboBox<ScopeOption> scopeBox = new JComboBox<>();
    private final JComboBox<String> scopeIdBox = new JComboBox<>();
    private final JLabel scopeIdLabel = new JLabel("Scope ID");
    private final JTextField priorityField = new JTextField("5", 8);
    private final JCheckBox enabledBox = new JCheckBox("Enabled", true);

    private final ConditionEditorPanel firstCondition;
    private final ConditionEditorPanel secondCondition;
    private final JCheckBox secondEnabled = new JCheckBox("Add a second condition");
    private final JComboBox<RuleDraft.Connector> connectorBox =
            new JComboBox<>(RuleDraft.Connector.values());
    private final JPanel secondSection = new JPanel(new BorderLayout(0, 4));

    private final JComboBox<ActionChoice> actionBox = new JComboBox<>();
    private final RuleValueEditor actionValue = new RuleValueEditor();
    private final JButton submitButton = new JButton("Validate & prepare rule");
    private final JButton clearButton = new JButton("Clear");
    private final JTextArea feedback = new JTextArea("Create a rule, then validate it.", 2, 25);

    public RuleBuilderPanel(CityMap city, SubmissionHandler handler) {
        this(city, () -> "USER-" + UUID.randomUUID(), handler);
    }

    /** Injectable ID supplier makes deterministic UI tests possible. */
    public RuleBuilderPanel(CityMap city, Supplier<String> newIdSupplier,
            SubmissionHandler handler) {
        this(city, newIdSupplier, new RuleExtensionRegistry(), handler);
    }

    public RuleBuilderPanel(CityMap city, Supplier<String> newIdSupplier,
            RuleExtensionRegistry extensions, SubmissionHandler handler) {
        super(new BorderLayout(8, 8));
        this.city = Objects.requireNonNull(city, "city");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
        this.builder = new RuleBuilder(city, extensions);
        this.firstCondition = new ConditionEditorPanel("Condition 1", extensions);
        this.secondCondition = new ConditionEditorPanel("Condition 2", extensions);
        this.newIdSupplier = Objects.requireNonNull(newIdSupplier, "newIdSupplier");
        this.handler = Objects.requireNonNull(handler, "handler");

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        form.add(buildDetailsPanel());
        form.add(Box.createVerticalStrut(8));
        form.add(buildScopePanel());
        form.add(Box.createVerticalStrut(8));
        form.add(firstCondition);
        form.add(Box.createVerticalStrut(8));
        form.add(buildSecondConditionPanel());
        form.add(Box.createVerticalStrut(8));
        form.add(buildActionPanel());
        form.add(Box.createVerticalGlue());
        add(new JScrollPane(form), BorderLayout.CENTER);

        feedback.setEditable(false);
        feedback.setFocusable(false);
        feedback.setOpaque(false);
        feedback.setLineWrap(true);
        feedback.setWrapStyleWord(true);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(submitButton);
        buttons.add(clearButton);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(feedback, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        targetBox.addActionListener(e -> refreshTarget());
        scopeBox.addActionListener(e -> refreshScopeIds());
        actionBox.addActionListener(e -> refreshAction());
        secondEnabled.addActionListener(e -> refreshSecondCondition());
        submitButton.addActionListener(e -> compileAndSubmit());
        clearButton.addActionListener(e -> clearForm());
        clearForm();
    }

    private JPanel buildDetailsPanel() {
        JPanel panel = titled("Rule details");
        addRow(panel, 0, "Name", nameField);
        addRow(panel, 1, "Target", targetBox);
        addRow(panel, 2, "Priority (integer)", priorityField);
        addRow(panel, 3, "State", enabledBox);
        return panel;
    }

    private JPanel buildScopePanel() {
        JPanel panel = titled("Target scope");
        addRow(panel, 0, "Scope", scopeBox);
        addRow(panel, 1, scopeIdLabel, scopeIdBox);
        return panel;
    }

    private JPanel buildSecondConditionPanel() {
        JPanel panel = titled("Boolean composition");
        panel.setLayout(new BorderLayout(0, 5));
        panel.add(secondEnabled, BorderLayout.NORTH);
        secondSection.add(connectorBox, BorderLayout.NORTH);
        secondSection.add(secondCondition, BorderLayout.CENTER);
        panel.add(secondSection, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildActionPanel() {
        JPanel panel = titled("Action");
        addRow(panel, 0, "Action type", actionBox);
        addRow(panel, 1, "Parameter", actionValue);
        return panel;
    }

    private static JPanel titled(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static void addRow(JPanel parent, int row, String label,
            java.awt.Component field) {
        addRow(parent, row, new JLabel(label), field);
    }

    private static void addRow(JPanel parent, int row, java.awt.Component label,
            java.awt.Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        parent.add(label, c);
        c.gridx = 1; c.weightx = 1;
        parent.add(field, c);
    }

    private void refreshTarget() {
        TargetCategory target = (TargetCategory) targetBox.getSelectedItem();
        if (target == null) return;
        scopeBox.removeAllItems();
        for (ScopeOption scope : RuleCatalogue.scopesFor(target)) scopeBox.addItem(scope);
        firstCondition.setTarget(target);
        secondCondition.setTarget(target);
        actionBox.removeAllItems();
        for (ActionChoice action : extensions.actionsFor(target)) actionBox.addItem(action);
        refreshScopeIds();
        refreshAction();
        refreshSecondCondition();
    }

    private void refreshScopeIds() {
        ScopeOption scope = (ScopeOption) scopeBox.getSelectedItem();
        scopeIdBox.removeAllItems();
        boolean needsId = scope != null && scope.needsId();
        if (needsId) {
            for (String id : RuleCatalogue.scopeIds(city, scope)) scopeIdBox.addItem(id);
        }
        scopeIdLabel.setVisible(needsId);
        scopeIdBox.setVisible(needsId);
    }

    private void refreshAction() {
        ActionChoice option = (ActionChoice) actionBox.getSelectedItem();
        actionValue.setValueKind(option == null
                ? com.smartcity.simulator.rules.builder.ValueKind.NONE
                : option.getValueKind());
    }

    private void refreshSecondCondition() {
        secondSection.setVisible(secondEnabled.isSelected());
        secondSection.revalidate();
        secondSection.repaint();
    }

    /** Extracts GUI selections without validating them. Safe even for invalid text. */
    public RuleDraft readDraft() {
        ScopeOption scope = (ScopeOption) scopeBox.getSelectedItem();
        boolean two = secondEnabled.isSelected();
        return new RuleDraft(nameField.getText(),
                (TargetCategory) targetBox.getSelectedItem(),
                scope,
                scope != null && scope.needsId()
                        ? (String) scopeIdBox.getSelectedItem() : null,
                firstCondition.readDraft(),
                two ? secondCondition.readDraft() : null,
                two ? (RuleDraft.Connector) connectorBox.getSelectedItem() : null,
                new RuleDraft.ActionDraft((ActionChoice) actionBox.getSelectedItem(),
                        actionValue.rawValue()),
                priorityField.getText(), enabledBox.isSelected());
    }

    /** Populates UI with a draft for a fresh rule (not an edit). */
    public void loadDraft(RuleDraft draft) {
        Objects.requireNonNull(draft, "draft");
        editingRuleId = null;
        showDraft(draft);
    }

    /** Stage 6.4 will use this to perform a validated replacement with the SAME ID. */
    public void loadForEditing(String existingId, RuleDraft draft) {
        if (existingId == null || existingId.isBlank()) {
            throw new IllegalArgumentException("Existing rule ID is required.");
        }
        Objects.requireNonNull(draft, "draft");
        showDraft(draft);
        editingRuleId = existingId;
        submitButton.setText("Validate replacement");
        feedback.setText("Editing rule " + existingId + " (not applied yet).");
    }

    private void showDraft(RuleDraft draft) {
        nameField.setText(draft.name());
        targetBox.setSelectedItem(draft.target());
        refreshTarget();
        scopeBox.setSelectedItem(draft.scope());
        refreshScopeIds();
        if (draft.scope() != null && draft.scope().needsId()) {
            scopeIdBox.setSelectedItem(draft.scopeId());
        }
        firstCondition.loadDraft(draft.firstCondition());
        secondEnabled.setSelected(draft.secondCondition() != null);
        if (draft.secondCondition() != null) {
            secondCondition.loadDraft(draft.secondCondition());
            connectorBox.setSelectedItem(draft.connector());
        }
        refreshSecondCondition();
        actionBox.setSelectedItem(draft.action().action());
        refreshAction();
        actionValue.setRawValue(draft.action().rawValue());
        priorityField.setText(draft.priority());
        enabledBox.setSelected(draft.enabled());
        if (editingRuleId == null) submitButton.setText("Validate & prepare rule");
        feedback.setForeground(Color.DARK_GRAY);
    }

    /** Called by the create button or by a verification harness. */
    public boolean compileAndSubmit() {
        try {
            RuleDraft draft = readDraft();
            String id = editingRuleId == null ? newIdSupplier.get() : editingRuleId;
            CompiledRule compiled = builder.build(id, draft);
            handler.onPrepared(id, draft, compiled);
            feedback.setForeground(new Color(0, 115, 54));
            feedback.setText(editingRuleId == null
                    ? "Rule prepared (not registered yet): " + draft.name().strip()
                    : "Replacement prepared (not applied yet): " + draft.name().strip());
            return true;
        } catch (IllegalArgumentException exception) {
            feedback.setForeground(new Color(170, 30, 30));
            feedback.setText("Validation: " + exception.getMessage());
            return false;
        }
    }

    public String getFeedbackText() {
        return feedback.getText();
    }

    /** Used by the integrated manager after an engine command is acknowledged. */
    public void showLifecycleStatus(String message, boolean success) {
        feedback.setForeground(success ? new Color(0, 115, 54) : new Color(170, 30, 30));
        feedback.setText(Objects.requireNonNull(message));
    }

    public boolean isEditing() {
        return editingRuleId != null;
    }

    public void clearForm() {
        editingRuleId = null;
        submitButton.setText("Validate & prepare rule");
        nameField.setText("");
        targetBox.setSelectedItem(TargetCategory.VEHICLE);
        refreshTarget();
        scopeBox.setSelectedItem(ScopeOption.ALL);
        secondEnabled.setSelected(false);
        refreshSecondCondition();
        priorityField.setText("5");
        enabledBox.setSelected(true);
        feedback.setForeground(Color.DARK_GRAY);
        feedback.setText("Create a rule, then validate it.");
    }
}
