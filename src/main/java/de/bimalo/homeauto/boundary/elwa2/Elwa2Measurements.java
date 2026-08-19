package de.bimalo.homeauto.boundary.elwa2;

import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public record Elwa2Measurements(
        Temperature currentTemperature,
        Temperature targetTemperature,
        Power currentPower,
        Elwa2Status operatingStatus,
        Instant measuredAt) {

    public boolean targetTemperatureReached() {
        return currentTemperature.getCelsius() >= targetTemperature.getCelsius();
    }

    public boolean isHeating() {
        return currentPower.isPositive();
    }

}