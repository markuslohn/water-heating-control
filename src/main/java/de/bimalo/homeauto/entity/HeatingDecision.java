package de.bimalo.homeauto.entity;

import java.util.Objects;

/**
 * Complete actuator decision for one water-heating control cycle.
 */
public record HeatingDecision(
        Power power,
        GasCommand gasCommand,
        HeatingSource source,
        boolean completed,
        String reason) {

    public HeatingDecision {
        Objects.requireNonNull(power, "power");
        Objects.requireNonNull(gasCommand, "gasCommand");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reason, "reason");
    }

    /** Compatibility constructor for callers interested only in power/source. */
    public HeatingDecision(Power power, HeatingSource source) {
        this(power, GasCommand.UNCHANGED, source, false, "Unspecified");
    }

    public static HeatingDecision electric(Power power, HeatingSource source, GasCommand gasCommand) {
        return new HeatingDecision(power, gasCommand, source, false, "Electric heating selected");
    }

    public static HeatingDecision gas(GasCommand gasCommand) {
        return new HeatingDecision(Power.ZERO, gasCommand, HeatingSource.GAS, false, "Gas fallback selected");
    }

    public static HeatingDecision idle(GasCommand gasCommand, String reason) {
        return new HeatingDecision(Power.ZERO, gasCommand, HeatingSource.NONE, false, reason);
    }

    public static HeatingDecision completed(GasCommand gasCommand, String reason) {
        return new HeatingDecision(Power.ZERO, gasCommand, HeatingSource.NONE, true, reason);
    }
}
