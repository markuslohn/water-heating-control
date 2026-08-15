package de.bimalo.homeauto.boundary.goecharger;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * go-eCharger Modbus register definition with automatic offset calculation.
 * See
 * https://github.com/goecharger/go-eCharger-API-v2/blob/main/modbus-de.md
 * for more details on the register definitions and their addresses.
 *
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum GoEchargerRegister {

    CAR_STATE(100, "Status PWM Signalisierung"),
    FWV(105, "Firmware Version"),
    SNR(304, "Seriennummer"),
    POWER_L1(146, "Leistung L1 in Watt"),
    POWER_L2(148, "Leistung L2 in Watt"),
    POWER_L3(150, "Leistung L3 in Watt");

    private static final int OFFSET = 1; // Modbus Holding Register Offset

    private final int handbookAddress;
    private final String description;

    /**
     * Returns the address for the modbus request (with offset correction)
     */
    public int getAddress() {
        return handbookAddress;
    }

    @Override
    public String toString() {
        return String.format("%s: Register %d (Request-Adresse: %d) - %s",
                name(), handbookAddress, getAddress(), description);
    }

}
