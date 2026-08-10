package de.bimalo.homeauto.boundary.viessman;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HotWaterProgram {

    INTERNAL_SHOULD_VALUE(0, "Interner Sollwert"),
    OFF(1, "Aus"),
    ON(2, "Ein"),
    FLOW_TEMPERATURE_SETPOINT(3, "Vorlauftemperatur-Sollwert"),
    MODULATION_SETPOINT(4, "Modulations-Sollwert"),
    UNKNOWN(-1, "Unbekanntes Programm");

    private final int value;
    private final String description;

    public static HotWaterProgram fromValue(int value) {
        for (HotWaterProgram program : values()) {
            if (program.value == value) {
                return program;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return String.format("%s (%d): %s", name(), value, description);
    }
}
