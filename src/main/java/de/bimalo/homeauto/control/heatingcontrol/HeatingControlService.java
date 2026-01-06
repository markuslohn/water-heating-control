package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Power;
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

    private static final int ZERO_WATTS = 0;
    private static final int MIN_POWER_WATTS = 0;

    private final HeatingControlConfig config;
    private final BatteryStorageService batteryStorageService;
    private final HeatingRodService heatingRodService;

    // Runtime override for battery priority (resets at midnight)
    private final AtomicBoolean batteryPriorityRuntimeOverride = new AtomicBoolean(false);
    private volatile LocalDate overrideDate = null;

    // Temperature hysteresis state: true when target reached and waiting for
    // temperature to drop
    private final AtomicBoolean targetReachedCoolingMode = new AtomicBoolean(false);

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
            // Read current heating power once per control cycle to avoid multiple service calls
            Power currentHeatingPower = heatingRodService.readPower();
            TemperatureCheck tempCheck = checkTemperature();

            // Check temperature with hysteresis
            if (!handleTemperatureHysteresis(tempCheck, currentHeatingPower)) {
                return; // Heating blocked by hysteresis or target reached
            }

            Power surplusPower = checkSurplusPower(currentHeatingPower);
            if (surplusPower.getWatts() < config.minSurplusPower()) {
                stopHeating(String.format("Surplus power %d W is below minimum %d W",
                        surplusPower.getWatts(), config.minSurplusPower()), currentHeatingPower);
                return;
            }

            // Limit heating power to configured maximum
            Power heatingPower = surplusPower.getWatts() > config.maxHeatingPower()
                    ? Power.ofWatts(config.maxHeatingPower())
                    : surplusPower;

            if (heatingPower.getWatts() < surplusPower.getWatts()) {
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
     * @param tempCheck the temperature check result
     * @param currentHeatingPower the current heating power
     * @return true if heating is allowed, false if blocked by hysteresis or target
     *         reached
     */
    private boolean handleTemperatureHysteresis(TemperatureCheck tempCheck, Power currentHeatingPower) {
        double hysteresis = config.temperatureHysteresis();
        double restartThreshold = tempCheck.targetCelsius() - hysteresis;

        // Guard Clause: Exit cooling mode if temperature dropped below restart threshold
        if (targetReachedCoolingMode.get() && tempCheck.currentCelsius() < restartThreshold) {
            targetReachedCoolingMode.set(false);
            log.info("Temperature dropped to %.1f°C (below restart threshold %.1f°C), exiting cooling mode",
                    tempCheck.currentCelsius(), restartThreshold);
            return true; // Allow heating to continue
        }

        // Guard Clause: Block heating if still in cooling mode
        if (targetReachedCoolingMode.get()) {
            log.debug("Cooling mode active: %.1f°C >= %.1f°C (target %.1f°C - hysteresis %.1f°C)",
                    tempCheck.currentCelsius(), restartThreshold,
                    tempCheck.targetCelsius(), hysteresis);
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
     * @param tempCheck the temperature check result
     * @param currentHeatingPower the current heating power
     * @param restartThreshold the temperature threshold for restarting heating
     */
    private void enterCoolingMode(TemperatureCheck tempCheck, Power currentHeatingPower, double restartThreshold) {
        if (targetReachedCoolingMode.compareAndSet(false, true)) {
            stopHeating(String.format("Target temperature %.1f°C reached (current: %.1f°C), entering cooling mode",
                    tempCheck.targetCelsius(), tempCheck.currentCelsius()), currentHeatingPower);
            log.info("Hysteresis active: Heating will restart when temperature drops below %.1f°C",
                    restartThreshold);
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
     * @param currentHeatingPower the current heating power (cached from control cycle)
     * @return Power object representing the adjusted surplus power available for
     *         heating
     */
    private Power checkSurplusPower(Power currentHeatingPower) {
        Power baseSurplus = batteryStorageService.determineSolarPowerSurplus();
        BatteryStatus batteryStatus = batteryStorageService.getCurrentStatus();

        long adjustedSurplus = calculateAdjustedSurplus(baseSurplus, currentHeatingPower);
        boolean batteryPriorityActive = config.batteryPriorityEnabled() && !batteryPriorityRuntimeOverride.get();
        int currentSoc = batteryStatus.getBatteryStateOfCharge().getValue();

        long availableForHeating;
        if (batteryPriorityActive && currentSoc < config.batteryPriorityThreshold()) {
            availableForHeating = calculateAvailablePowerBatteryPriority(
                    adjustedSurplus, batteryStatus, currentSoc);
        } else {
            availableForHeating = calculateAvailablePowerHeatingPriority(
                    adjustedSurplus, baseSurplus, currentHeatingPower, batteryStatus, currentSoc);
        }

        return Power.ofWatts(Math.max(MIN_POWER_WATTS, availableForHeating));
    }

    /**
     * Calculates the adjusted surplus power by adding current heating power back.
     * The current heating power is already included in house consumption
     * measurement,
     * so we need to add it back to get the real available surplus.
     *
     * @param baseSurplus         the base solar power surplus
     * @param currentHeatingPower the current heating rod power consumption
     * @return adjusted surplus in watts
     */
    private long calculateAdjustedSurplus(Power baseSurplus, Power currentHeatingPower) {
        return baseSurplus.getWatts() + currentHeatingPower.getWatts();
    }

    /**
     * Calculates available power for heating in battery priority mode.
     * Reserves power for battery charging and accounts for battery discharge.
     *
     * @param adjustedSurplus the adjusted solar surplus
     * @param batteryStatus   current battery status
     * @param currentSoc      current battery state of charge
     * @return available power for heating in watts
     */
    private long calculateAvailablePowerBatteryPriority(
            long adjustedSurplus, BatteryStatus batteryStatus, int currentSoc) {
        Power batteryPower = batteryStatus.getBatteryPower();
        long availableForHeating = adjustedSurplus;

        // If battery is discharging, reduce available power (insufficient solar)
        if (batteryPower.isNegative()) {
            availableForHeating += batteryPower.getWatts(); // batteryPower is negative, so this subtracts
        }

        // Reserve power for battery charging
        availableForHeating -= config.batteryReservedPower();

        log.info(
                "Battery priority active (SOC: {}% < {}%): Adjusted {} W, battery {} W, reserved {} W → {} W available",
                currentSoc, config.batteryPriorityThreshold(),
                adjustedSurplus, batteryPower.getWatts(), config.batteryReservedPower(),
                Math.max(MIN_POWER_WATTS, availableForHeating));

        return availableForHeating;
    }

    /**
     * Calculates available power for heating in heating priority mode.
     * Allows battery discharge to supplement heating power.
     *
     * @param adjustedSurplus     the adjusted solar surplus
     * @param baseSurplus         the base solar surplus (for logging)
     * @param currentHeatingPower current heating power (for logging)
     * @param batteryStatus       current battery status
     * @param currentSoc          current battery state of charge
     * @return available power for heating in watts
     */
    private long calculateAvailablePowerHeatingPriority(
            long adjustedSurplus, Power baseSurplus, Power currentHeatingPower,
            BatteryStatus batteryStatus, int currentSoc) {
        Power batteryPower = batteryStatus.getBatteryPower();
        long availableForHeating = adjustedSurplus;

        // Calculate available power including battery contribution
        availableForHeating += batteryPower.getWatts();

        log.info(
                "Heating priority mode: Adjusted {} W (base {} W + heating {} W), battery {} W, SOC {}% → {} W available",
                adjustedSurplus, baseSurplus.getWatts(), currentHeatingPower.getWatts(),
                batteryPower.getWatts(), currentSoc, Math.max(MIN_POWER_WATTS, availableForHeating));

        return availableForHeating;
    }

    /**
     * Stops the heating and logs the reason.
     *
     * @param reason the reason why heating is being stopped
     * @param currentHeatingPower the current heating power (cached from control cycle)
     */
    private void stopHeating(String reason, Power currentHeatingPower) {
        if (currentHeatingPower.getWatts() > ZERO_WATTS) {
            log.info("Stopping heating: {}", reason);
            heatingRodService.adjustHeating(Power.ofWatts(ZERO_WATTS));
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
     * @throws IllegalStateException if battery priority is not enabled in configuration
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
    }
}
