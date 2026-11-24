package com.smartcity.simulator.ui;

import com.smartcity.simulator.rules.builder.ConditionChoice;
import com.smartcity.simulator.rules.builder.RuleExtensionRegistry;
import com.smartcity.simulator.rules.builder.RuleCatalogue;
import com.smartcity.simulator.rules.builder.RuleDraft.ConditionDraft;
import com.smartcity.simulator.rules.builder.RuleOperator;
import com.smartcity.simulator.rules.builder.TargetCategory;
import com.smartcity.simulator.rules.builder.ValueKind;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** UI for one condition, including comparison, parameter and optional NOT. */
public final class ConditionEditorPanel extends JPanel {

    private final RuleExtensionRegistry extensions;
    private final JComboBox<ConditionChoice> conditionBox = new JComboBox<>();
    private final JComboBox<RuleOperator> operatorBox = new JComboBox<>();
    private final RuleValueEditor valueEditor = new RuleValueEditor();
    private final JCheckBox negateBox = new JCheckBox("NOT (invert this condition)");

    public ConditionEditorPanel(String title) {
        this(title, new RuleExtensionRegistry());
    }

    public ConditionEditorPanel(String title, RuleExtensionRegistry extensions) {
        super(new GridBagLayout());
        this.extensions = java.util.Objects.requireNonNull(extensions);
        setBorder(BorderFactory.createTitledBorder(title));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 6, 4, 6);

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        add(new JLabel("Condition"), c);
        c.gridx = 1; c.weightx = 1;
        add(conditionBox, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        add(new JLabel("Comparison"), c);
        c.gridx = 1; c.weightx = 1;
        add(operatorBox, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        add(new JLabel("Value"), c);
        c.gridx = 1; c.weightx = 1;
        add(valueEditor, c);

        c.gridx = 1; c.gridy = 3;
        add(negateBox, c);

        conditionBox.addActionListener(e -> refreshParameters());
    }

    public void setTarget(TargetCategory target) {
        conditionBox.removeAllItems();
        for (ConditionChoice option : extensions.conditionsFor(target)) {
            conditionBox.addItem(option);
        }
        if (conditionBox.getItemCount() > 0) {
            conditionBox.setSelectedIndex(0);
        }
        negateBox.setSelected(false);
        refreshParameters();
    }

    private void refreshParameters() {
        ConditionChoice option = (ConditionChoice) conditionBox.getSelectedItem();
        operatorBox.removeAllItems();
        if (option == null) {
            valueEditor.setValueKind(ValueKind.NONE);
            operatorBox.setEnabled(false);
            return;
        }
        for (RuleOperator operator : option.getOperators()) {
            operatorBox.addItem(operator);
        }
        operatorBox.setEnabled(operatorBox.getItemCount() > 0);
        valueEditor.setValueKind(option.getValueKind());
    }

    public ConditionDraft readDraft() {
        return new ConditionDraft(
                (ConditionChoice) conditionBox.getSelectedItem(),
                (RuleOperator) operatorBox.getSelectedItem(),
                valueEditor.rawValue(), negateBox.isSelected());
    }

    public void loadDraft(ConditionDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Condition draft is required.");
        }
        conditionBox.setSelectedItem(draft.condition());
        refreshParameters();
        operatorBox.setSelectedItem(draft.operator());
        valueEditor.setRawValue(draft.rawValue());
        negateBox.setSelected(draft.negated());
    }
}
