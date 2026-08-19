package de.bimalo.homeauto.entity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Data class for the main power data of the battery storage.
 */
@Getter
@Builder
public final class BatteryStatus {

    private final Instant measuredAt;

    private final Power productionPower;

    private final Power consumptionPower;

    private final Power batteryPower;

    private final Power gridPower;

    private final Percentage batteryStateOfCharge;

    @Override
    public String toString() {
        return String.format(
                "BatteryStatus[time= %s, PV-Power= %s, Battery-Power= %s, Home consumption= %s, Grid-Power= %s, Battery SOC= %s]",
                measuredAt,
                productionPower,
                batteryPower,
                consumptionPower,
                gridPower,
                batteryStateOfCharge);
    }

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
