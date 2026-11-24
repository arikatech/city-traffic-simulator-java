package com.smartcity.simulator.ui;

import com.smartcity.simulator.rules.builder.RuleCatalogue;
import com.smartcity.simulator.rules.builder.ValueKind;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Reusable editor for a text value, a fixed enum choice, or no parameter. */
final class RuleValueEditor extends JPanel {

    private static final String NONE = "none";
    private static final String TEXT = "text";
    private static final String CHOICE = "choice";

    private final CardLayout cards = new CardLayout();
    private final JPanel inputCards = new JPanel(cards);
    private final JTextField textField = new JTextField(12);
    private final JComboBox<String> choiceBox = new JComboBox<>();
    private final JLabel hintLabel = new JLabel(" ");
    private ValueKind kind = ValueKind.NONE;

    RuleValueEditor() {
        super(new BorderLayout(0, 4));
        inputCards.add(new JLabel("No value required"), NONE);
        inputCards.add(textField, TEXT);
        inputCards.add(choiceBox, CHOICE);
        add(inputCards, BorderLayout.CENTER);
        add(hintLabel, BorderLayout.SOUTH);
        setValueKind(ValueKind.NONE);
    }

    void setValueKind(ValueKind newKind) {
        kind = newKind;
        hintLabel.setText(kind.inputHint());
        List<String> values = RuleCatalogue.predefinedValues(kind);
        choiceBox.removeAllItems();
        for (String value : values) {
            choiceBox.addItem(value);
        }
        if (kind == ValueKind.NONE || kind == ValueKind.PRESENCE) {
            cards.show(inputCards, NONE);
        } else if (!values.isEmpty()) {
            cards.show(inputCards, CHOICE);
        } else {
            textField.setText("");
            cards.show(inputCards, TEXT);
        }
    }

    String rawValue() {
        if (kind == ValueKind.NONE || kind == ValueKind.PRESENCE) {
            return null;
        }
        if (!RuleCatalogue.predefinedValues(kind).isEmpty()) {
            return (String) choiceBox.getSelectedItem();
        }
        return textField.getText();
    }

    void setRawValue(String value) {
        if (kind == ValueKind.NONE || kind == ValueKind.PRESENCE) {
            return;
        }
        if (!RuleCatalogue.predefinedValues(kind).isEmpty()) {
            choiceBox.setSelectedItem(value);
        } else {
            textField.setText(value == null ? "" : value);
        }
    }
}
