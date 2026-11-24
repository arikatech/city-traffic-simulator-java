package com.smartcity.simulator.events;

import com.smartcity.simulator.city.EntryBlockable;

public final class RoadworkEvent extends TemporaryClosureEvent {

    public RoadworkEvent(
            String id,
            double startSeconds,
            double endSeconds,
            EntryBlockable... targets) {

        super(id, startSeconds, endSeconds, targets);
    }
}