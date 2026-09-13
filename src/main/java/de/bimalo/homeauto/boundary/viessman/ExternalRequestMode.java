package de.bimalo.homeauto.boundary.viessman;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * External request modes for Viessmann heating.
 */
@Getter
@RequiredArgsConstructor
public enum ExternalRequestMode {

    NO_CONNECTION(0, "No connection"),
    DIO_CONNECTION(1, "DIO connection is being established"),
    BACNET_CONNECTION(2, "BACnet connection is being established"),
    KNX_CONNECTION(3, "KNX connection is being established"),
    MODBUS_CONNECTION(4, "Modbus connection is being established"),
    EEBUS_CONNECTION(5, "EEBUS connection is being established"),
    STECK_CONNECTION(6, "Steck connection is being established"),
    UNKNOWN(-1, "Unknown mode");

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
