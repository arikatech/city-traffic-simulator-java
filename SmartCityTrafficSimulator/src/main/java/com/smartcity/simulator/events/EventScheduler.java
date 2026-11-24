package com.smartcity.simulator.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EventScheduler {
    private static final double EPSILON = 1e-9;

    private final EventContext context;
    private final Map<String, SimulationEvent> events = new LinkedHashMap<>();
    private double lastProcessedSeconds = -1.0;

    public EventScheduler(EventContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public void schedule(SimulationEvent event) {
        Objects.requireNonNull(event, "event");
        if (events.containsKey(event.getId())) {
            throw new IllegalArgumentException("Duplicate event ID: " + event.getId());
        }
        if (event.getStatus() != SimulationEvent.Status.SCHEDULED) {
            throw new IllegalArgumentException("Event was already started");
        }
        if (event.getStartSeconds() + EPSILON < lastProcessedSeconds) {
            throw new IllegalArgumentException("Cannot schedule an event in the past");
        }
        events.put(event.getId(), event);
    }

    public List<SimulationEvent> getEvents() {
        return List.copyOf(events.values());
    }

    public void processAt(double nowSeconds) {
        if (!Double.isFinite(nowSeconds) || nowSeconds < 0.0) {
            throw new IllegalArgumentException("Invalid simulation time");
        }
        if (nowSeconds + EPSILON < lastProcessedSeconds) {
            throw new IllegalArgumentException("Simulation time cannot go backwards");
        }

        // An end time is exclusive: [start, end).
        List<SimulationEvent> byEnd = new ArrayList<>(events.values());
        byEnd.sort(Comparator.comparingDouble(SimulationEvent::getEndSeconds)
                .thenComparing(SimulationEvent::getId));
        for (SimulationEvent event : byEnd) {
            if (event.getStatus() == SimulationEvent.Status.ACTIVE
                    && nowSeconds + EPSILON >= event.getEndSeconds()) {
                event.expire(context);
            }
        }

        // Start due events in chronological order, breaking ties by ID.
        List<SimulationEvent> byStart = new ArrayList<>(events.values());
        byStart.sort(Comparator.comparingDouble(SimulationEvent::getStartSeconds)
                .thenComparing(SimulationEvent::getId));
        for (SimulationEvent event : byStart) {
            if (event.getStatus() == SimulationEvent.Status.SCHEDULED
                    && nowSeconds + EPSILON >= event.getStartSeconds()) {
                event.activate(context);
                // Zero-duration events finish at the same boundary.
                if (nowSeconds + EPSILON >= event.getEndSeconds()) {
                    event.expire(context);
                }
            }
        }
        lastProcessedSeconds = nowSeconds;
    }
}