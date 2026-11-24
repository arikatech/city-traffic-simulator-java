package com.smartcity.simulator.simulation.view;

import java.util.List;
import java.util.Objects;

/** Immutable, read-only information for the selected map object. */
public record InspectionSnapshot(String title, String subtitle, List<Field> fields) {
    public InspectionSnapshot {
        title = Objects.requireNonNull(title);
        subtitle = Objects.requireNonNull(subtitle);
        fields = List.copyOf(Objects.requireNonNull(fields));
    }
    public record Field(String label, String value) {
        public Field {
            label = Objects.requireNonNull(label);
            value = Objects.requireNonNull(value);
        }
    }
    public static InspectionSnapshot message(String title, String message) {
        return new InspectionSnapshot(title, message, List.of());
    }
}
