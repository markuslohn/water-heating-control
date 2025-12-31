package de.bimalo.homeauto.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * Data class for the heating rod status.
 */
@Getter
@Builder
public final class HeatingStatus {

    private final boolean active;
    private final Power power;
    private final Temperature currentTemperature;
    private final Temperature targetTemperature;

    @Override
    public String toString() {
        return String.format(
                "HeatingStatus[active=%s, power=%s, currentTemp=%s, targetTemp=%s]",
                active, power, currentTemperature, targetTemperature);
    }
}
