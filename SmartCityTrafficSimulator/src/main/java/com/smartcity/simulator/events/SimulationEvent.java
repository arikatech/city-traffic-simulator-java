package com.smartcity.simulator.events;

public abstract class SimulationEvent {

    public enum Status {
        SCHEDULED,
        ACTIVE,
        COMPLETED
    }

    private final String id;
    private final double startSeconds;
    private final double endSeconds;
    private Status status = Status.SCHEDULED;

    protected SimulationEvent(String id, double startSeconds, double endSeconds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Event ID must not be blank");
        }
        if (!Double.isFinite(startSeconds) || startSeconds < 0.0) {
            throw new IllegalArgumentException("Invalid event start time");
        }
        if (!Double.isFinite(endSeconds) || endSeconds < startSeconds) {
            throw new IllegalArgumentException("Event end must be >= start");
        }
        this.id = id;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
    }

    public final String getId() {
        return id;
    }

    public final double getStartSeconds() {
        return startSeconds;
    }

    public final double getEndSeconds() {
        return endSeconds;
    }

    public final Status getStatus() {
        return status;
    }

    // Package-private: only the scheduler should control the lifecycle.
    final void activate(EventContext context) {
        if (status != Status.SCHEDULED) {
            throw new IllegalStateException("Event cannot start twice: " + id);
        }
        onStart(context);
        status = Status.ACTIVE;
    }

    final void expire(EventContext context) {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("Only active events can expire: " + id);
        }
        onEnd(context);
        status = Status.COMPLETED;
    }

    protected abstract void onStart(EventContext context);

    protected void onEnd(EventContext context) {
        // Optional: one-shot events need no cleanup.
    }
}