package de.bimalo.homeauto.entity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;

/**
 * Data class for the gas heating status.
 */
@Builder
public record GasHeatingStatus(
                boolean active,
                Temperature currentTemperature,
                Temperature targetTemperature,
                Instant measuredAt) {

        public boolean isOlderThan(Duration maximumAge, Clock clock) {
                return !measuredAt.plus(maximumAge)
                                .isAfter(clock.instant());
        }
}
