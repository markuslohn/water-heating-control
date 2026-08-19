package de.bimalo.homeauto.entity;

import java.time.Instant;

import de.bimalo.homeauto.boundary.goecharger.CarStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * Data class for the wallbox (electric vehicle charging) status.
 */
@Getter
@Builder
public final class WallboxStatus {

    private final CarStatus operatingStatus;
    private final Power chargingPower;
    private final Instant measuredAt;

    public boolean isCharging() {
        return operatingStatus == CarStatus.CHARGING;
    }

    @Override
    public String toString() {
        return String.format("WallboxStatus[operatingStatus=%s, chargingPower=%s]", operatingStatus, chargingPower);
    }
}
