package de.bimalo.homeauto.entity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;

@Builder
public record BatteryStatus(
        Instant measuredAt,
        Power productionPower,
        Power consumptionPower,
        Power batteryPower,
        Power gridPower,
        Percentage batteryStateOfCharge) {

    /**
     * Determines the pure solar power surplus available.
     * Only counts actual solar production, not battery discharge.
     *
     * @return Power surplus from solar only (battery discharge is not counted)
     */
    public Power determineSolarPowerSurplus() {
        // Base solar surplus: Production - Consumption
        Power surplusPower = productionPower.reduce(consumptionPower);

        // If battery is charging, this solar power is not available for other use
        if (batteryPower.isPositive()) {
            surplusPower = surplusPower.reduce(batteryPower);
        }

        // If battery is discharging (negative), we ignore it - it's not solar power
        if (surplusPower.isNegative()) {
            return Power.ofWatts(0);
        } else {
            return surplusPower;
        }
    }

    public boolean isOlderThan(Duration maximumAge, Clock clock) {
        return !measuredAt.plus(maximumAge)
                .isAfter(clock.instant());
    }
}
