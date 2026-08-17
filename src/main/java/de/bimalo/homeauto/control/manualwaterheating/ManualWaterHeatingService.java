package de.bimalo.homeauto.control.manualwaterheating;

import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.control.gasheating.GasHeatingService;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlConfig;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.HeatingSource;
import de.bimalo.homeauto.entity.ManualWaterHeatingStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Manual water heating mode: heats domestic hot water using PV surplus and,
 * once the heating rod temperature drops low enough, battery power - within
 * strict limits to never draw power from the grid. Falls back to gas heating
 * when no electrical power is available. Replaces the previous free-form
 * manual power control.
 * <p>
 * Started and stopped explicitly (e.g. via the dashboard); while active, it
 * suspends {@link HeatingControlService}'s automatic PV-surplus control.
 */
@Slf4j
@ApplicationScoped
public class ManualWaterHeatingService {

    private final ManualWaterHeatingConfig config;
    private final HeatingControlConfig heatingControlConfig;
    private final HeatingControlService heatingControlService;
    private final BatteryStorageService batteryStorageService;
    private final HeatingRodService heatingRodService;
    private final GasHeatingService gasHeatingService;

    private final AtomicBoolean active = new AtomicBoolean(false);

    // Hysteresis state: once triggered, stays active until its own stop condition,
    // regardless of the trigger condition fluctuating in between.
    private final AtomicBoolean rodTargetReached = new AtomicBoolean(false);
    private final AtomicBoolean batteryAssistActive = new AtomicBoolean(false);
    private final AtomicBoolean gasAssistActive = new AtomicBoolean(false);

    private volatile HeatingSource currentSource = HeatingSource.NONE;

    @Inject
    public ManualWaterHeatingService(
            ManualWaterHeatingConfig config,
            HeatingControlConfig heatingControlConfig,
            HeatingControlService heatingControlService,
            BatteryStorageService batteryStorageService,
            HeatingRodService heatingRodService,
            GasHeatingService gasHeatingService) {
        this.config = config;
        this.heatingControlConfig = heatingControlConfig;
        this.heatingControlService = heatingControlService;
        this.batteryStorageService = batteryStorageService;
        this.heatingRodService = heatingRodService;
        this.gasHeatingService = gasHeatingService;
    }

    /**
     * Starts manual water heating mode, suspending automatic PV-surplus control.
     */
    public void activate() {
        if (active.compareAndSet(false, true)) {
            rodTargetReached.set(false);
            batteryAssistActive.set(false);
            gasAssistActive.set(false);
            currentSource = HeatingSource.NONE;
            heatingControlService.activateManualMode();
            log.info("Manual water heating mode ACTIVATED");
        }
    }

    /**
     * Stops manual water heating mode: turns off the heating rod and gas
     * heating, and resumes automatic PV-surplus control.
     */
    public void deactivate() {
        if (active.compareAndSet(true, false)) {
            heatingRodService.adjustHeating(Power.ZERO);
            if (gasHeatingService.isHeatingActive()) {
                gasHeatingService.deactivateHeating();
            }
            currentSource = HeatingSource.NONE;
            heatingControlService.deactivateManualMode();
            log.info("Manual water heating mode DEACTIVATED");
        }
    }

    public ManualWaterHeatingStatus getStatus() {
        return ManualWaterHeatingStatus.builder()
                .active(active.get())
                .source(currentSource)
                .build();
    }

    @Scheduled(every = "40s")
    public void manageWaterHeating() {
        if (!active.get()) {
            return;
        }
        try {
            runCycle();
        } catch (Exception e) {
            log.error("Error during manual water heating control", e);
        }
    }

    private void runCycle() {
        Temperature rodCurrentTemp = heatingRodService.readTemperature1();
        Temperature rodTargetTemp = heatingRodService.readTargetTemperature();

        HeatingDecision decision = determineElectricHeatingPower(rodCurrentTemp, rodTargetTemp);
        heatingRodService.adjustHeating(decision.power());
        currentSource = decision.source();

        if (decision.power().isPositive()) {
            if (gasHeatingService.isHeatingActive()) {
                gasHeatingService.deactivateHeating();
            }
            gasAssistActive.set(false);
        } else {
            manageGasFallback();
        }
    }

    private HeatingDecision determineElectricHeatingPower(Temperature rodCurrentTemp, Temperature rodTargetTemp) {
        updateRodTargetReachedState(rodCurrentTemp, rodTargetTemp);
        if (rodTargetReached.get()) {
            batteryAssistActive.set(false);
            return new HeatingDecision(Power.ZERO, HeatingSource.NONE);
        }

        Power pvSurplus = batteryStorageService.determineSolarPowerSurplus();
        Power pvPower = pvSurplus.isGreaterThan(heatingControlConfig.maxHeatingPower())
                ? Power.ofWatts(heatingControlConfig.maxHeatingPower())
                : pvSurplus;

        BatteryStatus batteryStatus = batteryStorageService.getCurrentStatus();
        updateBatteryAssistState(rodCurrentTemp, batteryStatus.getBatteryStateOfCharge().getValue());

        Power batteryPower = batteryAssistActive.get()
                ? determineAvailableBatteryPower(batteryStatus)
                : Power.ZERO;

        Power totalPower = pvPower.increase(batteryPower);
        HeatingSource source;
        if (batteryPower.isPositive()) {
            source = HeatingSource.BATTERY;
        } else if (pvPower.isPositive()) {
            source = HeatingSource.PV;
        } else {
            source = HeatingSource.NONE;
        }
        return new HeatingDecision(totalPower, source);
    }

    private void updateRodTargetReachedState(Temperature current, Temperature target) {
        double hysteresis = heatingControlConfig.temperatureHysteresis();
        if (rodTargetReached.get() && current.getCelsius() < target.getCelsius() - hysteresis) {
            rodTargetReached.set(false);
        } else if (!rodTargetReached.get() && current.getCelsius() >= target.getCelsius()) {
            rodTargetReached.set(true);
            log.info("Manual water heating: rod target temperature reached ({}), stopping electric heating",
                    current);
        }
    }

    private void updateBatteryAssistState(Temperature rodCurrentTemp, int batterySocPercent) {
        if (!batteryAssistActive.get()
                && rodCurrentTemp.getCelsius() < config.heatingRodLowTemperatureThreshold()
                && batterySocPercent > config.batterySocStartThreshold()) {
            batteryAssistActive.set(true);
            log.info("Manual water heating: battery-assisted heating started (rod temp {}, SOC {}%)",
                    rodCurrentTemp, batterySocPercent);
        } else if (batteryAssistActive.get() && batterySocPercent <= config.batterySocStopThreshold()) {
            batteryAssistActive.set(false);
            log.info("Manual water heating: battery-assisted heating stopped (SOC reached {}%)", batterySocPercent);
        }
    }

    private Power determineAvailableBatteryPower(BatteryStatus status) {
        Power currentBatteryDischarge = status.getBatteryPower().isNegative()
                ? status.getBatteryPower().negate()
                : Power.ZERO;
        Power headroom = Power.ofWatts(config.batteryMaxDischargePower())
                .reduce(currentBatteryDischarge)
                .atLeast(Power.ZERO);
        return headroom.isGreaterThan(config.maxBatteryHeatingPower())
                ? Power.ofWatts(config.maxBatteryHeatingPower())
                : headroom;
    }

    private void manageGasFallback() {
        Temperature gasCurrentTemp = gasHeatingService.readHotWaterCurrentTemperature();
        Temperature gasTargetTemp = gasHeatingService.readHotWaterTargetTemperature();
        double shutoffTemp = gasTargetTemp.getCelsius() - config.gasHeatingShutoffTemperatureOffset();

        if (gasCurrentTemp.getCelsius() >= shutoffTemp) {
            gasAssistActive.set(false);
        } else if (!gasAssistActive.get() && gasCurrentTemp.getCelsius() < config.gasHeatingLowTemperatureThreshold()) {
            gasAssistActive.set(true);
            log.info("Manual water heating: gas heating fallback started (temp {})", gasCurrentTemp);
        }

        if (gasAssistActive.get()) {
            gasHeatingService.activateHeating();
            currentSource = HeatingSource.GAS;
        } else {
            if (gasHeatingService.isHeatingActive()) {
                gasHeatingService.deactivateHeating();
            }
            currentSource = HeatingSource.NONE;
        }
    }

    private record HeatingDecision(Power power, HeatingSource source) {
    }
}
