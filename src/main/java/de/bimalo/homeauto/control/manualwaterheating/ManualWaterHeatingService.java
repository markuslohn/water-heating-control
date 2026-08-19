package de.bimalo.homeauto.control.manualwaterheating;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlConfig;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
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
 * Started explicitly (e.g. via the dashboard); while active, it suspends
 * {@link HeatingControlService}'s automatic PV-surplus control. It stops
 * itself automatically as soon as one of the defined targets is reached (the
 * heating rod's target temperature, or - while gas heating was actively
 * running - the gas heating target), resuming automatic control.
 */
@Slf4j
@ApplicationScoped
public class ManualWaterHeatingService {

    private final ManualWaterHeatingConfig config;
    private final HeatingControlConfig heatingControlConfig;
    private final HeatingControlService heatingControlService;
    private final E3dcAdapter e3dcAdapter;
    private final Elwa2Adapter elwa2Adapter;
    private final VitodensAdapter vitodensAdapter;

    private final AtomicBoolean active = new AtomicBoolean(false);

    // Hysteresis state: once triggered, stays active until its own stop condition,
    // regardless of the trigger condition fluctuating in between.
    private final AtomicBoolean rodTargetReached = new AtomicBoolean(false);
    private final AtomicBoolean batteryAssistActive = new AtomicBoolean(false);
    private final AtomicBoolean gasAssistActive = new AtomicBoolean(false);

    // Battery SOC recorded when the current battery-assist session started, used to
    // enforce the maximum allowed SOC drop for that session.
    private volatile int batteryAssistStartSoc;

    private volatile HeatingSource currentSource = HeatingSource.NONE;

    @Inject
    public ManualWaterHeatingService(
            ManualWaterHeatingConfig config,
            HeatingControlConfig heatingControlConfig,
            HeatingControlService heatingControlService,
            E3dcAdapter e3dcAdapter,
            Elwa2Adapter elwa2Adapter,
            VitodensAdapter vitodensAdapter) {
        this.config = config;
        this.heatingControlConfig = heatingControlConfig;
        this.heatingControlService = heatingControlService;
        this.e3dcAdapter = e3dcAdapter;
        this.elwa2Adapter = elwa2Adapter;
        this.vitodensAdapter = vitodensAdapter;
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
            elwa2Adapter.adjustHeating(Power.ZERO);
            if (vitodensAdapter.isHeatingActive()) {
                vitodensAdapter.deactivateHeating();
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
        Temperature rodCurrentTemp = elwa2Adapter.readTemperature1();
        Temperature rodTargetTemp = elwa2Adapter.readTargetTemperature();

        HeatingDecision decision = determineElectricHeatingPower(rodCurrentTemp, rodTargetTemp);
        elwa2Adapter.adjustHeating(decision.power());
        currentSource = decision.source();

        if (rodTargetReached.get()) {
            log.info("Manual water heating: rod target temperature reached, deactivating manual mode");
            deactivate();
            return;
        }

        if (decision.power().isPositive()) {
            if (vitodensAdapter.isHeatingActive()) {
                vitodensAdapter.deactivateHeating();
            }
            gasAssistActive.set(false);
            return;
        }

        boolean gasTargetReached = manageGasFallback();
        if (gasTargetReached) {
            log.info("Manual water heating: gas heating target reached, deactivating manual mode");
            deactivate();
        }
    }

    private HeatingDecision determineElectricHeatingPower(Temperature rodCurrentTemp, Temperature rodTargetTemp) {
        updateRodTargetReachedState(rodCurrentTemp, rodTargetTemp);
        if (rodTargetReached.get()) {
            batteryAssistActive.set(false);
            return new HeatingDecision(Power.ZERO, HeatingSource.NONE);
        }

        Power pvSurplus = e3dcAdapter.determineSolarPowerSurplus();

        BatteryStatus batteryStatus = e3dcAdapter.getCurrentStatus();
        updateBatteryAssistState(rodCurrentTemp, batteryStatus.getBatteryStateOfCharge().getValue());

        Power batteryPower = batteryAssistActive.get()
                ? determineAvailableBatteryPower(batteryStatus)
                : Power.ZERO;

        Power totalPower = pvSurplus.increase(batteryPower);
        if (totalPower.isGreaterThan(heatingControlConfig.maxHeatingPower())) {
            totalPower = Power.ofWatts(heatingControlConfig.maxHeatingPower());
        }

        HeatingSource source;
        if (batteryPower.isPositive()) {
            source = HeatingSource.BATTERY;
        } else if (pvSurplus.isPositive()) {
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
            batteryAssistStartSoc = batterySocPercent;
            log.info("Manual water heating: battery-assisted heating started (rod temp {}, SOC {}%)",
                    rodCurrentTemp, batterySocPercent);
            return;
        }

        if (!batteryAssistActive.get()) {
            return;
        }

        boolean reachedAbsoluteFloor = batterySocPercent <= config.batterySocStopThreshold();
        int maxSocDrop = config.maxBatterySocDropPercent();
        boolean reachedSessionDropLimit = batterySocPercent <= batteryAssistStartSoc - maxSocDrop;

        if (reachedAbsoluteFloor || reachedSessionDropLimit) {
            batteryAssistActive.set(false);
            log.info(
                    "Manual water heating: battery-assisted heating stopped (SOC {}%, started at {}%, "
                            + "absolute floor reached: {}, session drop limit reached: {})",
                    batterySocPercent, batteryAssistStartSoc, reachedAbsoluteFloor, reachedSessionDropLimit);
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

    /**
     * @return true if gas heating was actively running this session and just reached its
     *         target - i.e. the gas heating goal was actually accomplished, as opposed to
     *         gas simply never having been needed
     */
    private boolean manageGasFallback() {
        boolean wasActive = gasAssistActive.get();
        Temperature gasCurrentTemp = vitodensAdapter.readHotWaterCurrentTemperature();
        Temperature gasTargetTemp = vitodensAdapter.readHotWaterTargetTemperature();
        double shutoffTemp = gasTargetTemp.getCelsius() - config.gasHeatingShutoffTemperatureOffset();

        if (gasCurrentTemp.getCelsius() >= shutoffTemp) {
            gasAssistActive.set(false);
        } else if (!gasAssistActive.get() && gasCurrentTemp.getCelsius() < config.gasHeatingLowTemperatureThreshold()) {
            gasAssistActive.set(true);
            log.info("Manual water heating: gas heating fallback started (temp {})", gasCurrentTemp);
        }

        if (gasAssistActive.get()) {
            vitodensAdapter.activateHeating();
            currentSource = HeatingSource.GAS;
        } else {
            if (vitodensAdapter.isHeatingActive()) {
                vitodensAdapter.deactivateHeating();
            }
            currentSource = HeatingSource.NONE;
        }

        return wasActive && !gasAssistActive.get();
    }

    private record HeatingDecision(Power power, HeatingSource source) {
    }
}
