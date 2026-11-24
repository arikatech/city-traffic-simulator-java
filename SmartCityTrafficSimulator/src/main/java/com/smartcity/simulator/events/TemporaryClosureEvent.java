package com.smartcity.simulator.events;

import com.smartcity.simulator.city.EntryBlockable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public abstract class TemporaryClosureEvent
        extends SimulationEvent {

    private final List<EntryBlockable> targets;
    private final String reason;

    protected TemporaryClosureEvent(
            String id,
            double startSeconds,
            double endSeconds,
            EntryBlockable... targets) {

        super(id, startSeconds, endSeconds);

        if (endSeconds <= startSeconds) {
            throw new IllegalArgumentException(
                    "Closure must have positive duration");
        }

        if (targets == null || targets.length == 0) {
            throw new IllegalArgumentException(
                    "At least one target is required");
        }

        LinkedHashSet<EntryBlockable> unique =
                new LinkedHashSet<>();

        for (EntryBlockable target : targets) {
            unique.add(
                    Objects.requireNonNull(target, "target")
            );
        }

        this.targets = List.copyOf(unique);
        this.reason = "event:" + id;
    }

    public final List<EntryBlockable> getTargets() {
        return targets;
    }

    @Override
    protected final void onStart(EventContext context) {
        for (EntryBlockable target : targets) {
            target.blockEntry(reason);
        }
    }

    @Override
    protected final void onEnd(EventContext context) {
        for (EntryBlockable target : targets) {
            target.unblockEntry(reason);
        }
    }
}