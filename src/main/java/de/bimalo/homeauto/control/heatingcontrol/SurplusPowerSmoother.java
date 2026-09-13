package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.entity.Power;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Smooths upward heating power adjustments by averaging recent surplus targets
 * over a configurable time window, while letting downward adjustments take
 * effect immediately. Adjustments below a configurable threshold are suppressed
 * to avoid reacting to short-lived surplus fluctuations (e.g. passing clouds).
 */
class SurplusPowerSmoother {

    private final Clock clock;
    private final Deque<Sample> samples = new ArrayDeque<>();

    SurplusPowerSmoother() {
        this(Clock.systemUTC());
    }

    SurplusPowerSmoother(Clock clock) {
        this.clock = clock;
    }

    /**
     * Determines the heating power that should actually be applied.
     *
     * @param instantSurplusTarget    the freshly computed, surplus-based target
     *                                power
     * @param currentHeatingPower     the heating power currently applied
     * @param smoothingWindow         time window over which upward adjustments are
     *                                averaged
     * @param minChangeThresholdWatts minimum power difference (in watts) required
     *                                to apply a change
     * @return the heating power to apply; equals {@code currentHeatingPower} if no
     *         adjustment should be made
     */
    Power determineTargetPower(
            Power instantSurplusTarget, Power currentHeatingPower, Duration smoothingWindow,
            int minChangeThresholdWatts) {
        Instant now = clock.instant();
        samples.addLast(new Sample(now, instantSurplusTarget));
        pruneOlderThan(now.minus(smoothingWindow));

        Power candidate = instantSurplusTarget.isLessThan(currentHeatingPower)
                ? instantSurplusTarget
                : average();

        Power delta = candidate.isGreaterThan(currentHeatingPower)
                ? candidate.reduce(currentHeatingPower)
                : currentHeatingPower.reduce(candidate);

        return delta.watts() < minChangeThresholdWatts ? currentHeatingPower : candidate;
    }

    private void pruneOlderThan(Instant cutoff) {
        while (!samples.isEmpty() && samples.peekFirst().timestamp().isBefore(cutoff)) {
            samples.removeFirst();
        }
    }

    private Power average() {
        long sum = 0;
        for (Sample sample : samples) {
            sum += sample.power().watts();
        }
        return Power.ofWatts(Math.round(sum / (double) samples.size()));
    }

    void reset() {
        samples.clear();
    }

    private record Sample(Instant timestamp, Power power) {
    }
}
