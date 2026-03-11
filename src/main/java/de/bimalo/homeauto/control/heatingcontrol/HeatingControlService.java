package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Season;
import de.bimalo.homeauto.entity.Temperature;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that controls the heating rod based on solar power surplus and
 * temperature.
 * Runs periodically to check conditions and adjust heating power accordingly.
 */
@Slf4j
@ApplicationScoped
public class HeatingControlService {

    private final HeatingControlConfig config;
    private final BatteryStorageService batteryStorageService;
    private final HeatingRodService heatingRodService;

    // Runtime override for battery priority (resets at midnight)
    private final AtomicBoolean batteryPriorityRuntimeOverride = new AtomicBoolean(false);
    private volatile LocalDate overrideDate = null;

    // Temperature hysteresis state: true when target reached and waiting for
    // temperature to drop
    private final AtomicBoolean targetReachedCoolingMode = new AtomicBoolean(false);

    // Manual mode: when active, automatic control is suspended
    private final AtomicBoolean manualModeActive = new AtomicBoolean(false);

    @Inject
    public HeatingControlService(
            HeatingControlConfig config,
            BatteryStorageService batteryStorageService,
            HeatingRodService heatingRodService) {
        this.config = config;
        this.batteryStorageService = batteryStorageService;
        this.heatingRodService = heatingRodService;
    }

    /**
     * Winter schedule: Runs during winter months (Nov-Feb) within configured hours.
     * Configurable via heatingctl.winter-cron and heatingctl.winter-enabled.
     */
    @Scheduled(cron = "{heatingctl.winter-cron}", skipExecutionIf = WinterDisabledPredicate.class)
    public void controlHeatingWinter() {
        log.debug("Winter schedule triggered");
        controlHeatingInternal();
    }

    /**
     * Spring schedule: Runs during spring months (Mar-Apr) within configured hours.
     * Configurable via heatingctl.spring-cron and heatingctl.spring-enabled.
     */
    @Scheduled(cron = "{heatingctl.spring-cron}", skipExecutionIf = SpringDisabledPredicate.class)
    public void controlHeatingSpring() {
        log.debug("Spring schedule triggered");
        controlHeatingInternal();
    }

    /**
     * Autumn schedule: Runs during autumn months (Sep-Oct) within configured hours.
     * Configurable via heatingctl.autumn-cron and heatingctl.autumn-enabled.
     */
    @Scheduled(cron = "{heatingctl.autumn-cron}", skipExecutionIf = AutumnDisabledPredicate.class)
    public void controlHeatingAutumn() {
        log.debug("Autumn schedule triggered");
        controlHeatingInternal();
    }

    /**
     * Summer schedule: Runs during summer months (May-Aug) within configured hours.
     * Configurable via heatingctl.summer-cron and heatingctl.summer-enabled.
     */
    @Scheduled(cron = "{heatingctl.summer-cron}", skipExecutionIf = SummerDisabledPredicate.class)
    public void controlHeatingSummer() {
        log.debug("Summer schedule triggered");
        controlHeatingInternal();
    }

    /**
     * Internal heating control logic called by seasonal schedulers.
     * Checks solar surplus and temperature to control the heating rod.
     */
    private void controlHeatingInternal() {
        if (!shouldControlHeating()) {
            return;
        }

        try {
            // Read current heating power once per control cycle to avoid multiple service
            // calls
            Power currentHeatingPower = heatingRodService.readPower();
            TemperatureCheck tempCheck = checkTemperature();

            // Manual mode: only monitor temperature, do not adjust heating automatically
            if (manualModeActive.get()) {
                if (tempCheck.targetReached()) {
                    log.info("Target temperature reached in manual mode - deactivating manual mode");
                    deactivateManualMode();
                    stopHeating("Target temperature reached in manual mode", currentHeatingPower);
                }
                return; // Skip automatic control while in manual mode
            }

            // Check temperature with hysteresis
            if (!handleTemperatureHysteresis(tempCheck, currentHeatingPower)) {
                return; // Heating blocked by hysteresis or target reached
            }

            Power surplusPower = checkSurplusPower(currentHeatingPower);
            if (surplusPower.isLessThan(config.minSurplusPower())) {
                stopHeating(String.format("Surplus power %d W is below minimum %d W",
                        surplusPower.getWatts(), config.minSurplusPower()), currentHeatingPower);
                return;
            }

            // Limit heating power to configured maximum
            Power heatingPower = surplusPower.isGreaterThan(config.maxHeatingPower())
                    ? Power.ofWatts(config.maxHeatingPower())
                    : surplusPower;

            if (heatingPower.isLessThan(surplusPower.getWatts())) {
                log.debug("Limiting heating power from {} W to {} W (max configured)",
                        surplusPower.getWatts(), heatingPower.getWatts());
            }

            adjustHeatingPower(heatingPower, tempCheck);

        } catch (Exception e) {
            log.error("Error during heating control", e);
        }
    }

    /**
     * Checks if heating control should run based on configuration.
     *
     * @return true if heating control should proceed
     */
    private boolean shouldControlHeating() {
        if (!config.enabled()) {
            log.debug("Heating control is disabled.");
        }
        return config.enabled();
    }

    /**
     * Checks the current and target temperatures.
     *
     * @return TemperatureCheck containing current and target temperatures
     */
    private TemperatureCheck checkTemperature() {
        Temperature currentTemperature = heatingRodService.readTemperature1();
        Temperature targetTemperature = heatingRodService.readTargetTemperature();

        log.debug("Current temperature: {}°C, Target temperature: {}°C",
                currentTemperature.getCelsius(), targetTemperature.getCelsius());

        return new TemperatureCheck(currentTemperature, targetTemperature);
    }

    /**
     * Handles temperature hysteresis to prevent frequent on/off cycling.
     * When target temperature is reached, heating only restarts when temperature
     * drops below (target - hysteresis).
     *
     * @param tempCheck           the temperature check result
     * @param currentHeatingPower the current heating power
     * @return true if heating is allowed, false if blocked by hysteresis or target
     *         reached
     */
    private boolean handleTemperatureHysteresis(TemperatureCheck tempCheck, Power currentHeatingPower) {
        double hysteresis = config.temperatureHysteresis();
        double restartThreshold = tempCheck.targetCelsius() - hysteresis;

        // Guard Clause: Exit cooling mode if temperature dropped below restart
        // threshold
        if (targetReachedCoolingMode.get() && tempCheck.currentCelsius() < restartThreshold) {
            targetReachedCoolingMode.set(false);
            log.info("Temperature dropped to {}°C (below restart threshold {}°C), exiting cooling mode",
                    String.format("%.1f", tempCheck.currentCelsius()),
                    String.format("%.1f", restartThreshold));
            return true; // Allow heating to continue
        }

        // Guard Clause: Block heating if still in cooling mode
        if (targetReachedCoolingMode.get()) {
            log.debug("Cooling mode active: {}°C >= {}°C (target {}°C - hysteresis {}°C)",
                    String.format("%.1f", tempCheck.currentCelsius()),
                    String.format("%.1f", restartThreshold),
                    String.format("%.1f", tempCheck.targetCelsius()),
                    String.format("%.1f", hysteresis));
            return false;
        }

        // Guard Clause: Enter cooling mode if target temperature reached
        if (tempCheck.targetReached()) {
            enterCoolingMode(tempCheck, currentHeatingPower, restartThreshold);
            return false;
        }

        // Happy Path: Normal operation, heating allowed
        return true;
    }

    /**
     * Enters cooling mode when target temperature is reached.
     * Extracted to eliminate code duplication.
     *
     * @param tempCheck           the temperature check result
     * @param currentHeatingPower the current heating power
     * @param restartThreshold    the temperature threshold for restarting heating
     */
    private void enterCoolingMode(TemperatureCheck tempCheck, Power currentHeatingPower, double restartThreshold) {
        if (targetReachedCoolingMode.compareAndSet(false, true)) {
            stopHeating(String.format("Target temperature %.1f°C reached (current: %.1f°C), entering cooling mode",
                    tempCheck.targetCelsius(), tempCheck.currentCelsius()), currentHeatingPower);
            log.info("Hysteresis active: Heating will restart when temperature drops below {}°C",
                    String.format("%.1f", restartThreshold));
        }
    }

    /**
     * Checks the available solar power surplus.
     * Adjusts the surplus by adding the current heating rod power, as it is already
     * included in the house consumption measurement.
     * If battery SOC is below threshold and battery priority is enabled (both
     * config and runtime),
     * reserves power for battery charging.
     *
     * @param currentHeatingPower the current heating power (cached from control
     *                            cycle)
     * @return Power object representing the adjusted surplus power available for
     *         heating
     */
    private Power checkSurplusPower(Power currentHeatingPower) {
        BatteryStatus batteryStatus = batteryStorageService.getCurrentStatus();
        Power baseSurplus = batteryStorageService.determineSolarPowerSurplus();
        PowerCalculationContext ctx = new PowerCalculationContext(batteryStatus, currentHeatingPower);

        Power adjustedSurplus = calculateAdjustedSurplus(ctx);
        boolean batteryPriorityActive = config.batteryPriorityEnabled() && !batteryPriorityRuntimeOverride.get();

        Power availableForHeating;
        if (batteryPriorityActive && ctx.getBatterySoc().isLessThan(config.batteryPriorityThreshold())) {
            availableForHeating = calculateAvailablePowerBatteryPriority(adjustedSurplus, ctx);
        } else {
            availableForHeating = calculateAvailablePowerHeatingPriority(adjustedSurplus, baseSurplus, ctx);
        }

        // Apply solar power reduction percentage
        availableForHeating = applySolarPowerReduction(availableForHeating);

        return availableForHeating.atLeast(Power.ZERO);
    }

    /**
     * Applies the configured solar power reduction percentage.
     * This provides a safety margin to avoid grid feed-in fluctuations.
     *
     * @param power the power to reduce
     * @return the reduced power
     */
    private Power applySolarPowerReduction(Power power) {
        int reductionPercent = config.solarPowerReductionPercent();
        if (reductionPercent <= 0 || power.getWatts() <= 0) {
            return power;
        }

        int reductionWatts = (int) ((long) power.getWatts() * reductionPercent / 100);
        Power reducedPower = power.reduce(reductionWatts);

        log.debug("Applied {}% solar power reduction: {} W → {} W (reduced by {} W)",
                reductionPercent, power.getWatts(), reducedPower.getWatts(), reductionWatts);

        return reducedPower;
    }

    /**
     * Calculates the adjusted surplus power available for heating.
     * The current heating power is already included in house consumption
     * measurement,
     * so it is subtracted to get the real available surplus.
     * Battery charging power is also considered as it reduces available surplus.
     *
     * @param ctx the power calculation context containing all relevant power values
     * @return adjusted surplus power available for heating
     */
    private Power calculateAdjustedSurplus(PowerCalculationContext ctx) {
        Power adjustedConsumptionPower;
        if (ctx.isHeatingActive()) {
            log.debug(
                    "Current heating power {} W is already included in house consumption measurement, adding back to surplus",
                    ctx.getCurrentHeatingPower().getWatts());
            adjustedConsumptionPower = ctx.getHouseConsumptionPower().reduce(ctx.getCurrentHeatingPower());
        } else {
            adjustedConsumptionPower = ctx.getHouseConsumptionPower();
        }

        // Base solar surplus: Production - Consumption
        Power surplusPower = ctx.getProductionPower().reduce(adjustedConsumptionPower);

        // If battery is charging, this solar power is not available for other use
        if (ctx.isBatteryCharging()) {
            surplusPower = surplusPower.reduce(ctx.getBatteryPower());
        }
        // If battery is discharging (negative), we ignore it - it's not solar power

        return surplusPower.atLeast(Power.ZERO);
    }

    /**
     * Calculates available power for heating in battery priority mode.
     * Reserves power for battery charging and accounts for battery discharge.
     *
     * @param adjustedSurplus the adjusted solar surplus
     * @param ctx             the power calculation context
     * @return available power for heating in watts
     */
    private Power calculateAvailablePowerBatteryPriority(Power adjustedSurplus, PowerCalculationContext ctx) {
        Power availableForHeating = adjustedSurplus;

        // If battery is discharging, reduce available power (insufficient solar)
        if (ctx.isBatteryDischarging()) {
            availableForHeating = availableForHeating.reduce(ctx.getBatteryPower());
        }

        // Reserve power for battery charging
        availableForHeating = availableForHeating.reduce(config.batteryReservedPower());

        log.info(
                "Battery priority active (SOC: {}% < {}%): Adjusted {} W, battery {} W, reserved {} W → {} W available",
                ctx.getBatterySoc(), config.batteryPriorityThreshold(),
                adjustedSurplus, ctx.getBatteryPower().getWatts(), config.batteryReservedPower(),
                availableForHeating.atLeast(Power.ZERO).getWatts());

        return availableForHeating;
    }

    /**
     * Calculates available power for heating in heating priority mode.
     * Allows battery discharge to supplement heating power.
     *
     * @param adjustedSurplus the adjusted solar surplus
     * @param baseSurplus     the base solar surplus before adjustments (for
     *                        logging)
     * @param ctx             the power calculation context
     * @return available power for heating in watts
     */
    private Power calculateAvailablePowerHeatingPriority(
            Power adjustedSurplus, Power baseSurplus, PowerCalculationContext ctx) {
        // Calculate available power including battery contribution
        Power availableForHeating = adjustedSurplus.increase(ctx.getBatteryPower());

        log.info(
                "Heating priority mode: Adjusted {} W (base {} W + heating {} W), battery {} W, SOC {}% → {} W available",
                adjustedSurplus.getWatts(), baseSurplus.getWatts(), ctx.getCurrentHeatingPower().getWatts(),
                ctx.getBatteryPower().getWatts(), ctx.getBatterySoc(),
                availableForHeating.atLeast(Power.ZERO).getWatts());

        return availableForHeating;
    }

    /**
     * Stops the heating and logs the reason.
     *
     * @param reason              the reason why heating is being stopped
     * @param currentHeatingPower the current heating power (cached from control
     *                            cycle)
     */
    private void stopHeating(String reason, Power currentHeatingPower) {
        if (currentHeatingPower.isPositive()) {
            log.info("Stopping heating: {}", reason);
            heatingRodService.adjustHeating(Power.ZERO);
        }
    }

    /**
     * Adjusts the heating power based on surplus power and temperature.
     *
     * @param surplusPower the available surplus power
     * @param tempCheck    the temperature check result
     */
    private void adjustHeatingPower(Power surplusPower, TemperatureCheck tempCheck) {
        log.info("Adjusting heating to {} W (temperature: {}°C, target: {}°C)",
                surplusPower.getWatts(), tempCheck.currentCelsius(), tempCheck.targetCelsius());
        heatingRodService.adjustHeating(surplusPower);
    }

    /**
     * Sets the battery priority runtime override.
     * When enabled, battery priority logic is temporarily disabled until midnight.
     *
     * @param disabled true to disable battery priority, false to enable it
     * @throws IllegalStateException if battery priority is not enabled in
     *                               configuration
     */
    public void setBatteryPriorityOverride(boolean disabled) {
        if (!config.batteryPriorityEnabled()) {
            log.warn("Cannot set battery priority override: Battery priority feature is disabled in configuration");
            throw new IllegalStateException(
                    "Battery priority feature is disabled in configuration (heatingctl.battery-priority-enabled=false). "
                            + "Enable it in configuration before using runtime override.");
        }

        this.batteryPriorityRuntimeOverride.set(disabled);
        if (disabled) {
            this.overrideDate = LocalDate.now();
            log.info("Battery priority DISABLED via runtime override (will reset at midnight)");
        } else {
            this.overrideDate = null;
            log.info("Battery priority ENABLED via runtime override");
        }
    }

    /**
     * Gets the current state of battery priority (considering both config and
     * runtime override).
     *
     * @return true if battery priority is active, false if disabled
     */
    public boolean isBatteryPriorityActive() {
        return config.batteryPriorityEnabled() && !batteryPriorityRuntimeOverride.get();
    }

    /**
     * Resets the battery priority override at midnight.
     * Runs every day at midnight to re-enable battery priority.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetBatteryPriorityOverride() {
        if (batteryPriorityRuntimeOverride.compareAndSet(true, false)) {
            log.info("Midnight reset: Re-enabling battery priority");
            overrideDate = null;
        }
        if (manualModeActive.compareAndSet(true, false)) {
            log.info("Midnight reset: Deactivating manual mode");
        }
    }

    /**
     * Activates manual mode, suspending automatic heating control.
     * In manual mode, the heating power is controlled externally (e.g., via REST
     * API)
     * and automatic adjustments are skipped until manual mode is deactivated.
     */
    public void activateManualMode() {
        if (manualModeActive.compareAndSet(false, true)) {
            log.info("Manual mode ACTIVATED - automatic heating control suspended");
        }
    }

    /**
     * Deactivates manual mode, resuming automatic heating control.
     * This is called when:
     * - The user explicitly sets heating power to 0
     * - The target temperature is reached while in manual mode
     */
    public void deactivateManualMode() {
        if (manualModeActive.compareAndSet(true, false)) {
            log.info("Manual mode DEACTIVATED - automatic heating control resumed");
        }
    }

    /**
     * Gets the current state of manual mode.
     *
     * @return true if manual mode is active, false if automatic control is active
     */
    public boolean isManualModeActive() {
        return manualModeActive.get();
    }

    /**
     * Gets the current season based on the current date.
     *
     * @return the current season
     */
    public Season getCurrentSeason() {
        return Season.current();
    }

    /**
     * Checks if the current season's schedule is enabled in configuration.
     *
     * @return true if the current season is enabled, false otherwise
     */
    public boolean isCurrentSeasonEnabled() {
        return switch (getCurrentSeason()) {
            case WINTER -> config.winterEnabled();
            case SPRING -> config.springEnabled();
            case SUMMER -> config.summerEnabled();
            case AUTUMN -> config.autumnEnabled();
        };
    }
}
