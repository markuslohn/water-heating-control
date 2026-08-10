package de.bimalo.homeauto.boundary.viessman;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * External request modes for Viessmann heating.
 */
@Getter
@RequiredArgsConstructor
public enum ExternalRequestMode {

    NO_CONNECTION(0, "Keine Verbindung"),
    DIO_CONNECTION(1, "DIO Verbindung wird aufgebaut"),
    BACNET_CONNECTION(2, "BACnet Verbindung wird aufgebaut"),
    KNX_CONNECTION(3, "KNX Verbindung wird aufgebaut"),
    MODBUS_CONNECTION(4, "Modbus Verbindung wird aufgebaut"),
    EEBUS_CONNECTION(5, "EEBUS Verbindung wird aufgebaut"),
    STECK_CONNECTION(6, "Steck Verbindung wird aufgebaut"),
    UNKNOWN(-1, "Unbekannter Modus");

    private final int value;
    private final String description;

    public static ExternalRequestMode fromValue(int value) {
        for (ExternalRequestMode status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return String.format("%s (%d): %s", name(), value, description);
    }
}
