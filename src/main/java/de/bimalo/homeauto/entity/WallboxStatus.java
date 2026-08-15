package de.bimalo.homeauto.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * Data class for the wallbox (electric vehicle charging) status.
 */
@Getter
@Builder
public final class WallboxStatus {

    private final boolean charging;
    private final Power chargingPower;

    @Override
    public String toString() {
        return String.format("WallboxStatus[charging=%s, chargingPower=%s]", charging, chargingPower);
    }
}
