package de.bimalo.homeauto.control.heatingcontrol;

import io.quarkus.scheduler.Scheduled.SkipPredicate;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Skip predicate for winter schedule.
 * Skips execution when winter schedule is disabled in configuration.
 */
@Singleton
public class WinterDisabledPredicate implements SkipPredicate {

    @Inject
    HeatingControlConfig config;

    @Override
    public boolean test(ScheduledExecution execution) {
        return !config.winterEnabled();
    }
}
