package de.bimalo.homeauto.boundary.viessman;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HotWaterStatus {

    OFF(0, "Hot water off"),
    ONLY_HOT_WATER(1, "Hot water only mode"),
    HEATING_AND_HOT_WATER(2, "Heating and hot water mode"),
    CHIMNEY_SWEEPING(3, "Chimney sweep mode"),
    TEST_MODE(4, "Test mode"),
    EXTERNAL_TEMPERATURE_CONTROL_SHOULD(5, "External temperature setpoint"),
    EXTERNAL_MODULATION_SHOULD(6, "External modulation setpoint"),
    HYGIENE(7, "Hygiene mode"),
    SOLAR_POWERED(8, "Solar mode"),
    AUTOMATIC(9, "Automatic mode"),
    UNKNOWN(-1, "Unknown status");

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
