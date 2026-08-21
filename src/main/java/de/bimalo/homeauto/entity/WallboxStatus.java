package de.bimalo.homeauto.entity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.Builder;

/**
 * Data class for the wallbox (electric vehicle charging) status.
 */
@Builder
public record WallboxStatus(CarStatus operatingStatus, Power chargingPower, Instant measuredAt) {

    public boolean isCharging() {
        return operatingStatus == CarStatus.CHARGING;
    }

    public boolean isOlderThan(Duration maximumAge, Clock clock) {
        return !measuredAt.plus(maximumAge)
                .isAfter(clock.instant());
    }

}
