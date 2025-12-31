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
}
