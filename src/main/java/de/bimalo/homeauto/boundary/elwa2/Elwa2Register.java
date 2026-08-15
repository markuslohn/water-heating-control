package de.bimalo.homeauto.boundary.elwa2;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ELWA2 Modbus register definition with automatic offset calculation.
 *
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum Elwa2Register {

    TEMP_1(1001, "Temp 1 (internal Sensor in AC ELWA 2)", 0.1),
    TARGET_TEMP(1002, "Tmax (target temperature solare powered)", 0.1),
    STATUS(1003, "Operating status", 1.0),
    POWER(1000, "Power", 1.0),
    MAX_POWER(1014, "max Power", 1.0),
    POWER_TIMEOUT(1004, "Power timeout", 1.0),
    SERIAL_NUMBER_2(1018, "AC ELWA 2 serial number 1-2", 1.0),
    SERIAL_NUMBER_4(1019, "AC ELWA 2 serial number 3-4", 1.0),
    SERIAL_NUMBER_6(1020, "AC ELWA 2 serial number 5-6", 1.0),
    SERIAL_NUMBER_8(1021, "AC ELWA 2 serial number 7-8", 1.0),
    SERIAL_NUMBER_10(1022, "AC ELWA 2 serial number 9-10", 1.0),
    SERIAL_NUMBER_12(1023, "AC ELWA 2 serial number 11-12", 1.0),
    SERIAL_NUMBER_14(1024, "AC ELWA 2 serial number 13-14", 1.0),
    SERIAL_NUMBER_16(1025, "AC ELWA 2 serial number 15-16", 1.0),
    FIRMWARE_VERSION(1016, "Controller firmware main version", 1.0);

    private final int handbookAddress;
    private final String description;
    private final double scaleFactor;

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
