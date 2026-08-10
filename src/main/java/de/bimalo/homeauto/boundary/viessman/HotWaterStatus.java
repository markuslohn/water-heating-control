package de.bimalo.homeauto.boundary.viessman;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HotWaterStatus {

    OFF(0, "Warmwasser aus"),
    ONLY_HOT_WATER(1, "Nur Warmwasserbetrieb"),
    HEATING_AND_HOT_WATER(2, "Heizung und Warmwasserbetrieb"),
    CHIMNEY_SWEEPING(3, "Kaminfegerbetrieb"),
    TEST_MODE(4, "Testbetrieb"),
    EXTERNAL_TEMPERATURE_CONTROL_SHOULD(5, "Externer Temperatur-Sollwert"),
    EXTERNAL_MODULATION_SHOULD(6, "Externe Modulation-Sollwert"),
    HYGIENE(7, "Hygienebetrieb"),
    SOLAR_POWERED(8, "Solarbetrieb"),
    AUTOMATIC(9, "Automatikbetrieb"),
    UNKNOWN(-1, "Unbekannter Status");

    private final int value;
    private final String description;

    public static HotWaterStatus fromValue(int value) {
        for (HotWaterStatus status : values()) {
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
