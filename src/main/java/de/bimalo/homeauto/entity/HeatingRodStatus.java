package de.bimalo.homeauto.entity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;

@Builder
public record HeatingRodStatus(
        Temperature currentTemperature,
        Temperature targetTemperature,
        Power currentPower,
        Instant measuredAt) {

    public boolean targetTemperatureReached() {
        return currentTemperature.celsius() >= targetTemperature.celsius();
    }

    public boolean isHeating() {
        return currentPower.isPositive();
    }

    public boolean isOlderThan(Duration maximumAge, Clock clock) {
        return !measuredAt.plus(maximumAge)
                .isAfter(clock.instant());
    }
}
