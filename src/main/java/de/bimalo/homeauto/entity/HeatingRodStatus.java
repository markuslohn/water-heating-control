package de.bimalo.homeauto.entity;

import de.bimalo.homeauto.boundary.elwa2.Elwa2OperatingStatus;
import lombok.Builder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Builder
public record HeatingRodStatus(
        Temperature currentTemperature,
        Temperature targetTemperature,
        Power currentPower,
        Elwa2OperatingStatus operatingStatus,
        Instant measuredAt) {

    public boolean targetTemperatureReached() {
        return currentTemperature.getCelsius() >= targetTemperature.getCelsius();
    }

    public boolean isHeating() {
        return currentPower.isPositive();
    }

    public boolean isOlderThan(Duration maximumAge, Clock clock) {
        return !measuredAt.plus(maximumAge)
                .isAfter(clock.instant());
    }
}
