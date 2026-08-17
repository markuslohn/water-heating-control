package de.bimalo.homeauto.control.heatingcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.bimalo.homeauto.entity.Power;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SurplusPowerSmootherTest {

    private static final Duration WINDOW = Duration.ofSeconds(150);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final SurplusPowerSmoother smoother = new SurplusPowerSmoother(clock);

    @Test
    void determineTargetPower_immediatelyAppliesDecrease_evenWithinSmoothingWindow() {
        Power result = smoother.determineTargetPower(Power.ofWatts(500), Power.ofWatts(2000), WINDOW, 0);

        assertEquals(500, result.getWatts());
    }

    @Test
    void determineTargetPower_appliesFirstIncreaseImmediately_asSingleSampleAverage() {
        Power result = smoother.determineTargetPower(Power.ofWatts(1500), Power.ofWatts(0), WINDOW, 0);

        assertEquals(1500, result.getWatts());
    }

    @Test
    void determineTargetPower_averagesIncreasesOverSamplesWithinWindow() {
        smoother.determineTargetPower(Power.ofWatts(1000), Power.ofWatts(0), WINDOW, 0);
        clock.advance(Duration.ofSeconds(40));
        smoother.determineTargetPower(Power.ofWatts(2000), Power.ofWatts(0), WINDOW, 0);
        clock.advance(Duration.ofSeconds(40));

        Power result = smoother.determineTargetPower(Power.ofWatts(3000), Power.ofWatts(0), WINDOW, 0);

        assertEquals(2000, result.getWatts()); // average of 1000, 2000, 3000
    }

    @Test
    void determineTargetPower_excludesSamplesOlderThanWindow() {
        smoother.determineTargetPower(Power.ofWatts(1000), Power.ofWatts(0), WINDOW, 0);
        clock.advance(WINDOW.plusSeconds(1));

        Power result = smoother.determineTargetPower(Power.ofWatts(3000), Power.ofWatts(0), WINDOW, 0);

        assertEquals(3000, result.getWatts()); // 1000 sample pruned, only 3000 remains
    }

    @Test
    void determineTargetPower_suppressesChangeBelowThreshold() {
        Power result = smoother.determineTargetPower(Power.ofWatts(1099), Power.ofWatts(1000), WINDOW, 100);

        assertEquals(1000, result.getWatts()); // delta of 99W < 100W threshold
    }

    @Test
    void determineTargetPower_appliesChangeAtExactThreshold() {
        Power result = smoother.determineTargetPower(Power.ofWatts(1100), Power.ofWatts(1000), WINDOW, 100);

        assertEquals(1100, result.getWatts()); // delta of exactly 100W meets the threshold
    }

    @Test
    void determineTargetPower_suppressesDecreaseBelowThreshold() {
        Power result = smoother.determineTargetPower(Power.ofWatts(950), Power.ofWatts(1000), WINDOW, 100);

        assertEquals(1000, result.getWatts()); // delta of 50W < 100W threshold, even though it's a decrease
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
