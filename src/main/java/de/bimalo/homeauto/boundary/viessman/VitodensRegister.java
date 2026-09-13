package de.bimalo.homeauto.boundary.viessman;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum VitodensRegister {

    STATUS(10009, "Connection status to the device", 1, 1),
    EXTERNAL_REQUEST(40003, "External request", 1, 1),
    EXTERNAL_REQUEST_STATUS(30001, "External request status", 1, 0),
    HOT_WATER_TARGET_TEMPERATUR(40004, "Hot water target temperature setpoint", 0.1, 10),
    HOT_WATER_HEATING_PROGRAMM_TARGET(40005, "Hot water operating program: setpoint", 1, 1),
    HOT_WATER_HEATING_PROGRAMM_CURRENT(40005, "Hot water operating program: actual", 1, 0),
    HOT_WATER_CURRENT_TEMPERATURE(30022, "Hot water temperature 271.0", 0.1, 0),
    OUTSIDE_TEMPERATURE(30009, "Outside temperature 274.0", 0.1, 0),
    HOT_WATER_STATUS(30024, "Hot water status 1659.1", 1, 0),
    HOT_WATER_GAS_CONSUMPTION_TODAY(30066, "Hot water gas consumption: today", 0.1, 0),
    HOT_WATER_GAS_CONSUMPTION_THIS_MONTH(30068, "Hot water gas consumption: this month", 0.1, 0);

    private final int handbookAddress;
    private final String description;
    private final double readFactor;
    private final double writeFactor;

    /**
     * Returns the address for the modbus request (with offset correction)
     */
    public int getAddress() {
        return handbookAddress;
    }

    @Override
    public String toString() {
        return String.format("%s: Register %d (request address: %d) - %s",
                name(), handbookAddress, getAddress(), description);
    }

}
