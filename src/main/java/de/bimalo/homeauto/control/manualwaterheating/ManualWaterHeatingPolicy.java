package de.bimalo.homeauto.control.manualwaterheating;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.GasCommand;
import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.HeatingDecision;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.HeatingSource;
import de.bimalo.homeauto.entity.ManualHeatingState;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Manual water heating mode: computes how domestic hot water should be
 * heated using PV surplus and, once the heating rod temperature drops low
 * enough, battery power - within strict limits to never draw power from the
 * grid. Falls back to gas heating when no electrical power is available.
 * <p>
 * This is a pure calculation component: it holds no device dependencies and
 * performs no device I/O. It only maintains its own {@link ManualHeatingState}
 * and, given the current device states, computes a {@link HeatingDecision}.
 * {@link de.bimalo.homeauto.control.heatingcontrol.HeatingControlService}
 * owns the schedule, decides when to call {@link #decide}, and executes the
 * resulting decision against the actual devices.
 */
@Slf4j
@ApplicationScoped
public class ManualWaterHeatingPolicy {

    private final ManualWaterHeatingConfig config;
    private final Clock clock;
    private volatile ManualHeatingState state = ManualHeatingState.inactive();

    @Inject
    public ManualWaterHeatingPolicy(ManualWaterHeatingConfig config) {
        this(config, Clock.systemUTC());
    }

    ManualWaterHeatingPolicy(ManualWaterHeatingConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    /**
     * Starts manual water heating mode.
     */
    public synchronized boolean start() {
        if (state.active()) {
            return false;
        }

        state = ManualHeatingState.started(clock.instant());
        log.info("Manual water heating mode ACTIVATED");
        return true;
    }

    /**
     * Stops manual water heating mode.
     */
    public synchronized boolean stop() {
        boolean gasWasActive = state.gasAssistActive();
        boolean wasActive = state.active();
        state = ManualHeatingState.inactive();
        if (wasActive) {
            log.info("Manual water heating mode deactivated");
        }
        return gasWasActive;
    }

    public synchronized ManualHeatingState getState() {
        return state;
    }

    /**
     * Computes the heating decision for the current cycle and updates the
     * internal {@link ManualHeatingState} accordingly. Must only be called
     * while manual mode is active.
     */
    public synchronized HeatingDecision decide(HeatingRodStatus rodStatus, BatteryStatus batteryStatus,
            GasHeatingStatus gasStatus) {

        if (!state.active()) {
            return HeatingDecision.idle(GasCommand.UNCHANGED, "Manual mode is inactive");
        }

        if (maximumDurationExceeded()) {
            return HeatingDecision.completed(gasStopCommand(), "Maximum manual-heating duration exceeded");
        }

        if (rodStatus.targetTemperatureReached()) {
            return HeatingDecision.completed(gasStopCommand(), "Heating-rod target temperature reached");
        }

        HeatingDecision electricDecision = determineElectricHeatingDecision(rodStatus, batteryStatus);
        return electricDecision.power().isPositive()
                ? electricDecision
                : manageGasFallback(gasStatus);
    }

    private HeatingDecision determineElectricHeatingDecision(HeatingRodStatus rodStatus, BatteryStatus batteryStatus) {
        state = updateBatteryAssistState(state, rodStatus.currentTemperature(),
                batteryStatus.batteryStateOfCharge().value());

        Power pvPower = batteryStatus.determineSolarPowerSurplus(rodStatus.currentPower());
        Power batteryPower = state.batteryAssistActive()
                ? determineAvailableBatteryPower(batteryStatus)
                : Power.ZERO;
        Power totalPower = pvPower.increase(batteryPower).atLeast(Power.ZERO);

        if (!totalPower.isPositive()) {
            return HeatingDecision.idle(GasCommand.UNCHANGED, "No electric power available");
        }

        HeatingSource source = determineElectricSource(pvPower, batteryPower);
        GasCommand gasCommand = state.gasAssistActive()
                ? GasCommand.DEACTIVATE
                : GasCommand.UNCHANGED;
        state = state.withGasAssist(false).withSource(source);
        return HeatingDecision.electric(totalPower, source, gasCommand);
    }

    private ManualHeatingState updateBatteryAssistState(ManualHeatingState current, Temperature rodCurrentTemp,
            int batterySocPercent) {
        if (!current.batteryAssistActive()
                && rodCurrentTemp.celsius() < config.heatingRodLowTemperatureThreshold()
                && batterySocPercent > config.batterySocStartThreshold()) {
            log.info("Battery-assisted heating started (rod temp {}, SOC {}%)",
                    rodCurrentTemp, batterySocPercent);
            return current.withBatteryAssist(true, batterySocPercent);
        }

        if (!current.batteryAssistActive()) {
            return current;
        }

        boolean reachedAbsoluteFloor = batterySocPercent <= config.batterySocStopThreshold();
        int maxSocDrop = config.maxBatterySocDropPercent();
        boolean reachedSessionDropLimit = batterySocPercent <= current.batteryAssistStartSoc() - maxSocDrop;

        if (reachedAbsoluteFloor || reachedSessionDropLimit) {
            log.info(
                    "Battery-assisted heating stopped (SOC {}%, started at {}%, "
                            + "absolute floor reached: {}, session drop limit reached: {})",
                    batterySocPercent, current.batteryAssistStartSoc(), reachedAbsoluteFloor, reachedSessionDropLimit);
            return current.withBatteryAssist(false, current.batteryAssistStartSoc());
        }

        return current;
    }

    private Power determineAvailableBatteryPower(BatteryStatus status) {
        Power currentBatteryDischarge = status.batteryPower().isNegative()
                ? status.batteryPower().negate()
                : Power.ZERO;
        Power headroom = Power.ofWatts(config.batteryMaxDischargePower())
                .reduce(currentBatteryDischarge)
                .atLeast(Power.ZERO);
        return headroom.isGreaterThan(config.maxBatteryHeatingPower())
                ? Power.ofWatts(config.maxBatteryHeatingPower())
                : headroom;
    }

    private HeatingDecision manageGasFallback(GasHeatingStatus gasStatus) {
        double gasCurrentTemp = gasStatus.currentTemperature().celsius();
        double gasTargetTemp = gasStatus.targetTemperature().celsius();
        double shutoffTemp = gasTargetTemp - config.gasHeatingShutoffTemperatureOffset();

        if (state.gasAssistActive() && gasCurrentTemp >= shutoffTemp) {
            return HeatingDecision.completed(GasCommand.DEACTIVATE, "Gas-heating shutoff temperature reached");
        }
        if (!state.gasAssistActive() && gasCurrentTemp < config.gasHeatingLowTemperatureThreshold()) {
            state = state.withGasAssist(true).withSource(HeatingSource.GAS);
            return HeatingDecision.gas(GasCommand.ACTIVATE);
        }
        if (state.gasAssistActive()) {
            state = state.withSource(HeatingSource.GAS);
            return HeatingDecision.gas(GasCommand.UNCHANGED);
        }

        state = state.withSource(HeatingSource.NONE);
        return HeatingDecision.idle(GasCommand.UNCHANGED, "Waiting for heat source");
    }

    private boolean maximumDurationExceeded() {
        Duration maximumDuration = config.maximumDuration();
        return maximumDuration != null && !clock.instant().isBefore(state.startedAt().plus(maximumDuration));
    }

    private GasCommand gasStopCommand() {
        return state.gasAssistActive() ? GasCommand.DEACTIVATE : GasCommand.UNCHANGED;
    }

    private HeatingSource determineElectricSource(Power pvPower, Power batteryPower) {
        if (pvPower.isPositive() && batteryPower.isPositive()) {
            return HeatingSource.PV_AND_BATTERY;
        }
        return batteryPower.isPositive() ? HeatingSource.BATTERY : HeatingSource.PV;
    }
}
