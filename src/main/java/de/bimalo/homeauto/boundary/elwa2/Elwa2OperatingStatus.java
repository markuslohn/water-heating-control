package de.bimalo.homeauto.boundary.elwa2;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Operating status for ELWA2 heating rod.
 */
@Getter
@RequiredArgsConstructor
public enum Elwa2OperatingStatus {
    NO_CONTROL(1, "No control"),
    HEAT(2, "Heat"),
    STANDBY(3, "Standby"),
    BOOST_HEAT(4, "Boost heat"),
    HEAT_FINISHED(5, "Heat finished"),
    LEGIONELLA_BOOST_ACTIVE(20, "Legionella-Boost active"),
    DEVICE_DISABLED(21, "Device disabled"),
    DEVICE_BLOCKED(22, "Device blocked"),
    STL_TRIGGERED(201, "STL triggered"),
    POWER_STAGE_OVERTEMP(202, "Power stage overtemp"),
    POWER_STAGE_PCB_TEMP_PROBE_FAULT(203, "Power stage PCB temp probe fault"),
    HARDWARE_FAULT(204, "Hardware fault"),
    ELWA_TEMP_SENSOR_FAULT(205, "ELWA Temp Sensor fault"),
    MAINBOARD_ERROR(209, "Mainboard Error"),
    UNKNOWN(-1, "Unknown status");

    private final int value;
    private final String description;

    /**
     * Converts a raw value into an Elwa2OperatingStatus.
     *
     * @param value the raw value from the Modbus register
     * @return the corresponding status, or UNKNOWN if the value is not defined
     */
    public static Elwa2OperatingStatus fromValue(int value) {
        for (Elwa2OperatingStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }

    /**
     * Checks whether the status indicates an error.
     *
     * @return true if this is an error status (value >= 200)
     */
    public boolean isError() {
        return value >= 200;
    }

    /**
     * Checks whether the heating rod is actively heating.
     *
     * @return true if HEAT or BOOST_HEAT
     */
    public boolean isHeating() {
        return this == HEAT || this == BOOST_HEAT || this == LEGIONELLA_BOOST_ACTIVE;
    }

    @Override
    public String toString() {
        return String.format("%s (%d): %s", name(), value, description);
    }
}
