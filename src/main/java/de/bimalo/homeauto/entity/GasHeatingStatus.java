package de.bimalo.homeauto.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * Data class for the gas heating status.
 */
@Getter
@Builder
public final class GasHeatingStatus {

    private final boolean active;
    private final Temperature currentTemperature;
    private final Temperature targetTemperature;

    @Override
    public String toString() {
        return String.format("GasHeatingStatus[active=%s, currentTemp=%s, targetTemp=%s]",
                active, currentTemperature, targetTemperature);
    }
}
