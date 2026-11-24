package com.smartcity.simulator.events;

import com.smartcity.simulator.city.EntryBlockable;

public final class AccidentEvent extends TemporaryClosureEvent {

    public AccidentEvent(
            String id,
            double startSeconds,
            double endSeconds,
            EntryBlockable... targets) {

        super(id, startSeconds, endSeconds, targets);
    }
}