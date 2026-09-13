package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import java.time.Instant;

/**
 * REST response for the battery status endpoint. Wraps {@link BatteryStatus}
 * with {@code stale}, since freshness is a boundary/API concern that the
 * domain entity itself (also used by the control logic) should not carry.
 */
public record BatteryStatusResponse(
        Power productionPower,
        Power consumptionPower,
        Power batteryPower,
        Power gridPower,
        Percentage batteryStateOfCharge,
        boolean stale,
        Instant measuredAt) {

    static BatteryStatusResponse of(BatteryStatus status, boolean stale) {
        return new BatteryStatusResponse(
                status.productionPower(),
                status.consumptionPower(),
                status.batteryPower(),
                status.gridPower(),
                status.batteryStateOfCharge(),
                stale,
                status.measuredAt());
    }
}
