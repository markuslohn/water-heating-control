package de.bimalo.homeauto.control.heatingcontrol;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;
import java.time.Duration;

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
    @WithDefault("2500")
    int maxHeatingPower();

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
    @WithDefault("7.0")
    double temperatureHysteresis();

    /**
     * Percentage to reduce available solar power.
     * This provides a safety margin to avoid grid feed-in fluctuations.
     * Example: 5 means reduce available power by 5% (2000W → 1900W).
     */
    @Min(0)
    @WithDefault("5")
    int solarPowerReductionPercent();

    /**
     * Time window over which upward heating power adjustments are averaged to
     * smooth out short-lived surplus spikes (e.g. passing clouds). Downward
     * adjustments are not affected and take effect immediately.
     * Supports duration expressions like "150s", "2m30s".
     */
    @WithDefault("150s")
    Duration powerIncreaseSmoothingWindow();

    /**
     * Minimum difference in watts between the currently applied heating power and
     * the newly determined power required to trigger an adjustment. Prevents
     * chattering adjustments for minor surplus fluctuations.
     */
    @Min(0)
    @WithDefault("100")
    int minPowerChangeThreshold();

    // ========== Seasonal Operating Hours ==========

    /**
     * Enable winter schedule (Nov-Feb).
     */
    @WithDefault("true")
    boolean winterEnabled();

    /**
     * Cron expression for winter schedule (Nov-Feb).
     * Default: Every 40 seconds, 09:00-15:59, Nov/Dec/Jan/Feb
     */
    @WithDefault("*/40 * 9-15 * 11,12,1,2 ?")
    String winterCron();

    /**
     * Enable spring schedule (Mar-Apr).
     */
    @WithDefault("true")
    boolean springEnabled();

    /**
     * Cron expression for spring schedule (Mar-Apr).
     * Default: Every 40 seconds, 08:00-17:59, Mar/Apr
     */
    @WithDefault("*/40 * 8-17 * 3,4 ?")
    String springCron();

    /**
     * Enable autumn schedule (Sep-Oct).
     */
    @WithDefault("true")
    boolean autumnEnabled();

    /**
     * Cron expression for autumn schedule (Sep-Oct).
     * Default: Every 40 seconds, 08:00-17:59, Sep/Oct
     */
    @WithDefault("*/40 * 8-17 * 9,10 ?")
    String autumnCron();

    /**
     * Enable summer schedule (May-Aug).
     */
    @WithDefault("true")
    boolean summerEnabled();

    /**
     * Cron expression for summer schedule (May-Aug).
     * Default: Every 40 seconds, 07:00-19:59, May-Aug
     */
    @WithDefault("*/40 * 7-19 * 5-8 ?")
    String summerCron();
}
