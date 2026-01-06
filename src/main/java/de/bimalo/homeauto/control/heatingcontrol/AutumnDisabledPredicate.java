package de.bimalo.homeauto.control.heatingcontrol;

import io.quarkus.scheduler.Scheduled.SkipPredicate;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Skip predicate for autumn schedule.
 * Skips execution when autumn schedule is disabled in configuration.
 */
@Singleton
public class AutumnDisabledPredicate implements SkipPredicate {

    @Inject
    HeatingControlConfig config;

    @Override
    public boolean test(ScheduledExecution execution) {
        return !config.autumnEnabled();
    }
}
