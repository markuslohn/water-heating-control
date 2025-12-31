package de.bimalo.homeauto.boundary.elwa2;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Betriebsstatus des ELWA2 Heizstabs.
 */
@Getter
@RequiredArgsConstructor
public enum Elwa2Status {
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
     * Konvertiert einen Rohwert in einen Elwa2Status.
     *
     * @param value der Rohwert aus dem Modbus-Register
     * @return den entsprechenden Status, oder UNKNOWN wenn der Wert nicht definiert ist
     */
    public static Elwa2Status fromValue(int value) {
        for (Elwa2Status status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }

    /**
     * Prüft, ob der Status einen Fehler anzeigt.
     *
     * @return true wenn es sich um einen Fehlerstatus handelt (Wert >= 200)
     */
    public boolean isError() {
        return value >= 200;
    }

    /**
     * Prüft, ob der Heizstab aktiv heizt.
     *
     * @return true wenn HEAT oder BOOST_HEAT
     */
    public boolean isHeating() {
        return this == HEAT || this == BOOST_HEAT || this == LEGIONELLA_BOOST_ACTIVE;
    }

    @Override
    public String toString() {
        return String.format("%s (%d): %s", name(), value, description);
    }
}
