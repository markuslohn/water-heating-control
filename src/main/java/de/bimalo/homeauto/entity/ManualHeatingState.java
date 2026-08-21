package de.bimalo.homeauto.entity;

import java.time.Instant;

public record ManualHeatingState(
        boolean active,
        boolean batteryAssistActive,
        boolean gasAssistActive,
        int batteryAssistStartSoc,
        Instant startedAt,
        HeatingSource source) {

    public static ManualHeatingState inactive() {
        return new ManualHeatingState(
                false,
                false,
                false,
                0,
                Instant.EPOCH,
                HeatingSource.NONE);
    }

    public static ManualHeatingState started(Instant startedAt) {
        return new ManualHeatingState(
                true,
                false,
                false,
                0,
                startedAt,
                HeatingSource.NONE);
    }

    public ManualHeatingState withBatteryAssist(boolean enabled, int startSoc) {
        return new ManualHeatingState(
                active, enabled, gasAssistActive, startSoc, startedAt, source);
    }

    public ManualHeatingState withGasAssist(boolean enabled) {
        return new ManualHeatingState(
                active, batteryAssistActive, enabled, batteryAssistStartSoc, startedAt, source);
    }

    public ManualHeatingState withSource(HeatingSource newSource) {
        return new ManualHeatingState(
                active, batteryAssistActive, gasAssistActive, batteryAssistStartSoc, startedAt, newSource);
    }
}
