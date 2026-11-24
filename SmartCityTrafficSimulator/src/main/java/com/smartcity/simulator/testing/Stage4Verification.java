package com.smartcity.simulator.testing;

import com.smartcity.simulator.city.CityMap;
import com.smartcity.simulator.environment.EnvironmentState;
import com.smartcity.simulator.environment.Weather;
import com.smartcity.simulator.events.EventContext;
import com.smartcity.simulator.events.EventScheduler;
import com.smartcity.simulator.events.SimulationEvent;
import com.smartcity.simulator.events.WeatherChangeEvent;

public final class Stage4Verification {
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class CountingEvent extends SimulationEvent {
        private int starts;
        private int ends;

        CountingEvent(String id, double startSeconds, double endSeconds) {
            super(id, startSeconds, endSeconds);
        }

        @Override
        protected void onStart(EventContext context) { starts++; }

        @Override
        protected void onEnd(EventContext context) { ends++; }
    }

    public static void main(String[] args) {
        EnvironmentState environment = new EnvironmentState();
        EventScheduler scheduler = new EventScheduler(
                new EventContext(new CityMap(), environment));

        WeatherChangeEvent rain = new WeatherChangeEvent("RAIN-1", 0.25, Weather.RAIN);
        CountingEvent roadworkLike = new CountingEvent("TEST-TIMED", 0.35, 0.65);
        scheduler.schedule(rain);
        scheduler.schedule(roadworkLike);

        check(environment.getWeather() == Weather.DRY, "Weather initially DRY");
        scheduler.processAt(0.0);
        scheduler.processAt(0.1);
        scheduler.processAt(0.2);
        check(environment.getWeather() == Weather.DRY, "Rain must not start early");
        check(rain.getStatus() == SimulationEvent.Status.SCHEDULED, "Still scheduled");

        scheduler.processAt(0.3);
        check(environment.getWeather() == Weather.RAIN, "Rain starts on next tick boundary");
        check(rain.getStatus() == SimulationEvent.Status.COMPLETED, "One-shot event completed");
        scheduler.processAt(0.4);
        check(environment.getWeather() == Weather.RAIN, "Weather persists after event");
        check(roadworkLike.starts == 1 && roadworkLike.ends == 0, "Timed event active");
        scheduler.processAt(0.7);
        check(roadworkLike.starts == 1 && roadworkLike.ends == 1, "Timed event expired");
        check(roadworkLike.getStatus() == SimulationEvent.Status.COMPLETED, "Timed event complete");
        scheduler.processAt(0.7);
        check(roadworkLike.starts == 1 && roadworkLike.ends == 1, "No duplicate callbacks");

        boolean duplicateRejected = false;
        try {
            scheduler.schedule(new WeatherChangeEvent("RAIN-1", 1.0, Weather.DRY));
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        check(duplicateRejected, "Duplicate IDs rejected");

        scheduler.schedule(new WeatherChangeEvent("DRY-1", 0.9, Weather.DRY));
        scheduler.processAt(0.9);
        check(environment.getWeather() == Weather.DRY, "Later event changes weather back");

        System.out.println("STAGE 4.1 PASSED! Checks: " + checks);
    }
}