package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Instant;

/**
 * REST response for the gas heating status endpoint. Wraps
 * {@link GasHeatingStatus} with {@code stale}.
 */
public record GasHeatingStatusResponse(
        boolean active,
        Temperature currentTemperature,
        Temperature targetTemperature,
        boolean stale,
        Instant measuredAt) {

    static GasHeatingStatusResponse of(GasHeatingStatus status, boolean stale) {
        return new GasHeatingStatusResponse(
                status.active(),
                status.currentTemperature(),
                status.targetTemperature(),
                stale,
                status.measuredAt());
    }
}
