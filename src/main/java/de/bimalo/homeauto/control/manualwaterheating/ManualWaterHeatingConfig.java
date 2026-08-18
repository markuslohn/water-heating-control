package de.bimalo.homeauto.control.manualwaterheating;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;

/**
 * Configuration for the manual water heating mode.
 * Allows heating hot water from PV surplus and, when the heating rod
 * temperature drops low enough, from battery power - within strict limits to
 * never draw power from the grid. Falls back to gas heating when no
 * electrical power is available.
 */
@ConfigMapping(prefix = "manualwaterheating")
public interface ManualWaterHeatingConfig {

    /**
     * Heating rod temperature (in °C) below which battery-assisted heating is
     * allowed to start (if the battery SOC is also above
     * {@link #batterySocStartThreshold()}).
     */
    @WithDefault("42.0")
    double heatingRodLowTemperatureThreshold();

    /**
     * Battery state of charge (in percent) above which battery-assisted
     * heating is allowed to start.
     */
    @Min(0)
    @WithDefault("65")
    int batterySocStartThreshold();

    /**
     * Battery state of charge (in percent) at or below which battery-assisted
     * heating stops, to avoid depleting the battery further.
     */
    @Min(0)
    @WithDefault("50")
    int batterySocStopThreshold();

    /**
     * Maximum battery state of charge drop (in percentage points), measured
     * from the SOC at the moment battery-assisted heating started, before it
     * stops - regardless of the absolute {@link #batterySocStopThreshold()}.
     * Example: assist starts at 85% SOC with a limit of 10 -&gt; stops once SOC
     * reaches 75%, even if that is still above the absolute stop threshold.
     */
    @Min(0)
    @WithDefault("10")
    int maxBatterySocDropPercent();

    /**
     * Maximum power (in watts) the battery may contribute to heating.
     */
    @Min(0)
    @WithDefault("800")
    int maxBatteryHeatingPower();

    /**
     * Maximum discharge power (in watts) the battery can deliver in total,
     * including power already used to cover house consumption.
     */
    @Min(0)
    @WithDefault("1400")
    int batteryMaxDischargePower();

    /**
     * Gas heating hot water temperature (in °C) below which gas heating is
     * used as a fallback when no electrical power is available for heating.
     */
    @WithDefault("35.0")
    double gasHeatingLowTemperatureThreshold();

    /**
     * Offset (in °C) below the gas heating target temperature at which gas
     * heating is switched off, since some hot water is still produced for a
     * while after shutoff (thermal inertia).
     */
    @Min(0)
    @WithDefault("2.0")
    double gasHeatingShutoffTemperatureOffset();
}
