package de.bimalo.homeauto.boundary.goecharger;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Status of the vehicle connected to the go-eCharger charging station.
 * Corresponds to the value of register {@link GoEchargerRegister#CAR_STATE}.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum CarStatus {

    DEVICE_FAULT(0, "Unbekannt, Ladestation defekt"),
    READY(1, "Ladestation bereit, kein Fahrzeug"),
    CHARGING(2, "Fahrzeug wird geladen"),
    WAITING_FOR_VEHICLE(3, "Warte auf Fahrzeug"),
    CHARGING_FINISHED(4, "Ladung beendet, Fahrzeug noch verbunden"),
    UNKNOWN(-1, "Unbekannter Status");

    private final int code;
    private final String description;

    /**
     * Returns the {@code CarStatus} for the given numeric code.
     *
     * @param code the numeric status value
     * @return the matching {@code CarStatus}, or {@link #UNKNOWN} if no match was
     *         found
     */
    public static CarStatus fromCode(int code) {
        for (CarStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return String.format("%s(%d): %s", name(), code, description);
    }

}
