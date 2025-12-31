package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
     *
     * @return Power object representing the adjusted surplus power available for
     *         heating
     */
    private Power checkSurplusPower() {
        Power baseSurplus = batteryStorageService.determineSolarPowerSurplus();
        Power currentHeatingPower = heatingRodService.readPower();

        // Add current heating power back to surplus (it's already included in house
        // consumption)
        long adjustedSurplus = baseSurplus.getWatts() + currentHeatingPower.getWatts();

        log.debug("Solar surplus: {} W (base: {} W + current heating: {} W)",
                adjustedSurplus, baseSurplus.getWatts(), currentHeatingPower.getWatts());

        return Power.ofWatts(adjustedSurplus);
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
}
