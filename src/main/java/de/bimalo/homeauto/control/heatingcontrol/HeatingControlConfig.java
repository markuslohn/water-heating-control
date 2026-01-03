package de.bimalo.homeauto.control.heatingcontrol;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;

/**
 * Configuration for the heating control system.
 * Controls the heating rod based on solar surplus and temperature.
 */
@ConfigMapping(prefix = "heatingctl")
public interface HeatingControlConfig {

    /**
     * Whether the heating control is enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Minimum surplus power in watts to start heating.
     * Prevents heating with very small surplus amounts.
     */
    @Min(0)
    @WithDefault("100")
    int minSurplusPower();

    /**
     * Maximum heating power in watts.
     * Limits the heating power even if more surplus is available.
     */
    @Min(1)
    @WithDefault("3000")
    int maxHeatingPower();

    /**
     * Schedule interval for the heating control check.
     * Supports duration expressions like "1m", "30s", "2m30s".
     */
    @WithDefault("1m")
    String scheduleInterval();

    /**
     * Master switch to enable/disable battery priority feature completely.
     * If false, battery priority logic is never applied regardless of SOC.
     */
    @WithDefault("true")
    boolean batteryPriorityEnabled();

    /**
     * Battery state of charge threshold (in percent) below which battery charging
     * has priority.
     * If the battery SOC is below this threshold, a portion of surplus power is
     * reserved for battery charging instead of heating.
     */
    @Min(0)
    @WithDefault("60")
    int batteryPriorityThreshold();

    /**
     * Power reserved for battery charging (in watts) when battery SOC is below
     * threshold.
     * This amount will be subtracted from available surplus to ensure the battery
     * gets charged.
     */
    @Min(0)
    @WithDefault("1000")
    int batteryReservedPower();

    /**
     * Temperature hysteresis in degrees Celsius.
     * When target temperature is reached, heating only restarts when temperature
     * drops below (target - hysteresis).
     * This prevents frequent on/off cycling.
     */
    @Min(0)
    @WithDefault("10.0")
    double temperatureHysteresis();
}
