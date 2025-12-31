package de.bimalo.homeauto.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Builder;
import lombok.Getter;

/**
 * Data class for the main power data of the battery storage.
 */
@Getter
@Builder
public final class BatteryStatus {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final LocalDateTime timestamp;

    private final Power productionPower;

    private final Power consumptionPower;

    private final Power batteryPower;

    private final Power gridPower;

    private final Percentage batteryStateOfCharge;

    @Override
    public String toString() {
        return String.format(
                "BatteryStatus[time= %s, PV-Power= %s, Battery-Power= %s, Home consumption= %s, Grid-Power= %s, Battery SOC= %s]",
                timestamp != null ? timestamp.format(FORMATTER) : "null",
                productionPower,
                batteryPower,
                consumptionPower,
                gridPower,
                batteryStateOfCharge);
    }
}
