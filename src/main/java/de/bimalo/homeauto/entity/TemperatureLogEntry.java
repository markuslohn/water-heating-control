package de.bimalo.homeauto.entity;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * A single entry of the daily temperature protocol, recording the heating rod
 * and gas heating hot water temperatures at a point in time.
 */
@Getter
@Builder
public final class TemperatureLogEntry {

    private final Instant timestamp;
    private final Temperature heatingRodTemperature;
    private final Temperature gasHeatingTemperature;

    @Override
    public String toString() {
        return String.format("TemperatureLogEntry[timestamp=%s, heatingRod=%s, gasHeating=%s]",
                timestamp, heatingRodTemperature, gasHeatingTemperature);
    }
}
