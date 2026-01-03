package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
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
    private volatile boolean batteryPriorityRuntimeOverride = false;
    private volatile LocalDate overrideDate = null;

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
     * Periodically checks solar surplus and temperature to control the heating rod.
     * The interval is configurable via heatingctl.schedule-interval.
     */
    @Scheduled(every = "{heatingctl.schedule-interval}")
    public void controlHeating() {
        log.info("controlHeating invoked.");
        if (!shouldControlHeating()) {
            return;
        }

        try {
            TemperatureCheck tempCheck = checkTemperature();
            if (tempCheck.targetReached()) {
                stopHeating(String.format("Target temperature %.1f°C reached (current: %.1f°C)",
                        tempCheck.targetCelsius(), tempCheck.currentCelsius()));
                return;
            }

            Power surplusPower = checkSurplusPower();
            if (surplusPower.getWatts() < config.minSurplusPower()) {
                stopHeating(String.format("Surplus power %d W is below minimum %d W",
                        surplusPower.getWatts(), config.minSurplusPower()));
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
            return false;
        }
        return true;
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
     * Checks the available solar power surplus.
     * Adjusts the surplus by adding the current heating rod power, as it is already
     * included in the house consumption measurement.
     * If battery SOC is below threshold and battery priority is enabled (both config and runtime),
     * reserves power for battery charging.
     *
     * @return Power object representing the adjusted surplus power available for
     *         heating
     */
    private Power checkSurplusPower() {
        Power baseSurplus = batteryStorageService.determineSolarPowerSurplus();
        Power currentHeatingPower = heatingRodService.readPower();
        Power batteryPower = batteryStorageService.getCurrentStatus().getBatteryPower();

        // Add current heating power back to surplus (it's already included in house
        // consumption)
        long adjustedSurplus = baseSurplus.getWatts() + currentHeatingPower.getWatts();

        // Check if battery priority is active (both config and runtime override must be enabled)
        boolean batteryPriorityActive = config.batteryPriorityEnabled() && !batteryPriorityRuntimeOverride;
        int currentSoc = batteryStorageService.getCurrentStatus().getBatteryStateOfCharge().getValue();
        long availableForHeating = adjustedSurplus;

        // If battery priority is DISABLED and battery is charging, add that power back
        // (heating has priority over battery charging)
        if (!batteryPriorityActive && batteryPower.isPositive()) {
            availableForHeating += batteryPower.getWatts();
            log.debug("Battery priority disabled: Adding battery charging power {} W to available heating power",
                    batteryPower.getWatts());
        }

        if (batteryPriorityActive && currentSoc < config.batteryPriorityThreshold()) {
            // Reserve power for battery charging
            availableForHeating = adjustedSurplus - config.batteryReservedPower();

            log.info("Battery priority active (SOC: {}% < {}%): Reserving {} W for battery, {} W available for heating",
                    currentSoc, config.batteryPriorityThreshold(),
                    config.batteryReservedPower(), Math.max(0, availableForHeating));
        } else {
            if (!batteryPriorityActive && currentSoc < config.batteryPriorityThreshold()) {
                log.info("Battery priority DISABLED (override active) - Solar surplus: {} W, Battery SOC: {}%",
                        adjustedSurplus, currentSoc);
            } else {
                log.info("Solar surplus: {} W (base: {} W + current heating: {} W), Battery SOC: {}%",
                        adjustedSurplus, baseSurplus.getWatts(), currentHeatingPower.getWatts(), currentSoc);
            }
        }

        // Ensure we don't return negative values
        return Power.ofWatts(Math.max(0, availableForHeating));
    }

    /**
     * Stops the heating and logs the reason.
     *
     * @param reason the reason why heating is being stopped
     */
    private void stopHeating(String reason) {
        Power currentHeatingPower = heatingRodService.readPower();
        if (currentHeatingPower.getWatts() > 0) {
            log.info("Stopping heating: {}", reason);
            heatingRodService.adjustHeating(Power.ofWatts(0));
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
     */
    public void setBatteryPriorityOverride(boolean disabled) {
        this.batteryPriorityRuntimeOverride = disabled;
        if (disabled) {
            this.overrideDate = LocalDate.now();
            log.info("Battery priority DISABLED via runtime override (will reset at midnight)");
        } else {
            this.overrideDate = null;
            log.info("Battery priority ENABLED via runtime override");
        }
    }

    /**
     * Gets the current state of battery priority (considering both config and runtime override).
     *
     * @return true if battery priority is active, false if disabled
     */
    public boolean isBatteryPriorityActive() {
        return config.batteryPriorityEnabled() && !batteryPriorityRuntimeOverride;
    }

    /**
     * Resets the battery priority override at midnight.
     * Runs every day at midnight to re-enable battery priority.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetBatteryPriorityOverride() {
        if (batteryPriorityRuntimeOverride) {
            log.info("Midnight reset: Re-enabling battery priority");
            batteryPriorityRuntimeOverride = false;
            overrideDate = null;
        }
    }
}
