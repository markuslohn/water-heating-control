package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.entity.CarStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.WallboxStatus;
import java.time.Instant;

/**
 * REST response for the wallbox status endpoint. Wraps {@link WallboxStatus}
 * with {@code stale}; keeps the {@code charging} derived JSON property the
 * dashboard already relies on.
 */
public record WallboxStatusResponse(
        CarStatus operatingStatus,
        Power chargingPower,
        boolean stale,
        Instant measuredAt) {

    public boolean isCharging() {
        return operatingStatus == CarStatus.CHARGING;
    }

    static WallboxStatusResponse of(WallboxStatus status, boolean stale) {
        return new WallboxStatusResponse(status.operatingStatus(), status.chargingPower(), stale, status.measuredAt());
    }
}
