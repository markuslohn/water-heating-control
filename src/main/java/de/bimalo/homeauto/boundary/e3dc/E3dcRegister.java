package de.bimalo.homeauto.boundary.e3dc;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * E33DC Modbus register definition with automatic offset calculation.
 *
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum E3dcRegister {

    MANUFACTURER(40004, "Hersteller"),
    MODEL(40020, "Modell"),
    SERIAL_NUMBER(40036, "Seriennummer"),
    FIRMWARE_VERSION(40052, "Firmware-Version"),
    PV_POWER(40068, "PV-Leistung in W"),
    PV_EXTENDED_POWER(40076, "Leistung aller zusätzlichen Einspeiser in Watt "),
    BATTERY_POWER(40070, "Batterieleistung in W"),
    HOUSE_POWER(40072, "Hausverbrauch in W"),
    GRID_POWER(40074, "Netzleistung in W"),
    BATTERY_SOC(40083, "Batterieladestand in %");

    private static final int OFFSET = 40001; // Modbus Holding Register Offset

    private final int handbookAddress;
    private final String description;

    /**
     * Returns the address for the modbus request (with offset correction)
     */
    public int getAddress() {
        return handbookAddress - OFFSET;
    }

    @Override
    public String toString() {
        return String.format("%s: Register %d (Request-Adresse: %d) - %s",
                name(), handbookAddress, getAddress(), description);
    }
}
