package de.bimalo.homeauto.boundary.viessman;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HotWaterProgram {

    INTERNAL_SHOULD_VALUE(0, "Internal setpoint"),
    OFF(1, "Off"),
    ON(2, "On"),
    FLOW_TEMPERATURE_SETPOINT(3, "Flow temperature setpoint"),
    MODULATION_SETPOINT(4, "Modulation setpoint"),
    UNKNOWN(-1, "Unknown program");

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
