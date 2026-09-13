package de.bimalo.homeauto.control.heatingcontrol;

import io.quarkus.scheduler.Scheduled.SkipPredicate;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Skip predicate for summer schedule.
 * Skips execution when summer schedule is disabled in configuration.
 */
@Singleton
public class SummerDisabledPredicate implements SkipPredicate {

    private final HeatingControlConfig config;

    @Inject
    public SummerDisabledPredicate(HeatingControlConfig config) {
        this.config = config;
    }

    @Override
    public boolean test(ScheduledExecution execution) {
        return !config.summerEnabled();
    }
}
