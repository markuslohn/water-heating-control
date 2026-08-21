package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.GasCommand;
import de.bimalo.homeauto.entity.HeatingDecision;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.HeatingSource;
import de.bimalo.homeauto.entity.Power;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/** Pure automatic-heating policy apart from its hysteresis/smoothing state. */
@Slf4j
@ApplicationScoped
public class AutomaticHeatingPolicy {

    private final HeatingControlConfig config;
    private final SurplusPowerSmoother smoother = new SurplusPowerSmoother();
    private boolean coolingMode;

    @Inject
    public AutomaticHeatingPolicy(HeatingControlConfig config) {
        this.config = config;
    }

    public synchronized HeatingDecision decide(
            HeatingRodStatus rodStatus,
            BatteryStatus batteryStatus,
            boolean batteryPriorityActive) {

        TemperatureCheck temperatures = new TemperatureCheck(
                rodStatus.currentTemperature(),
                rodStatus.targetTemperature());

        if (!temperatureAllowsHeating(temperatures)) {
            return HeatingDecision.idle(GasCommand.UNCHANGED, "Temperature hysteresis active");
        }

        Power availablePower = determineAvailablePower(
                batteryStatus,
                rodStatus.currentPower(),
                batteryPriorityActive);

        if (availablePower.isLessThan(config.minSurplusPower())) {
            return HeatingDecision.idle(GasCommand.UNCHANGED, "PV surplus below minimum");
        }

        Power limitedPower = availablePower.isGreaterThan(config.maxHeatingPower())
                ? Power.ofWatts(config.maxHeatingPower())
                : availablePower;
        Power targetPower = smoother.determineTargetPower(
                limitedPower,
                rodStatus.currentPower(),
                config.powerIncreaseSmoothingWindow(),
                config.minPowerChangeThreshold());

        return HeatingDecision.electric(targetPower, HeatingSource.PV, GasCommand.UNCHANGED);
    }

    public synchronized void reset() {
        coolingMode = false;
        smoother.reset();
    }

    private boolean temperatureAllowsHeating(TemperatureCheck temperatures) {
        double restartTemperature = temperatures.targetCelsius() - config.temperatureHysteresis();

        if (coolingMode && temperatures.currentCelsius() < restartTemperature) {
            coolingMode = false;
        }
        if (coolingMode) {
            return false;
        }
        if (temperatures.targetReached()) {
            coolingMode = true;
            return false;
        }
        return true;
    }

    private Power determineAvailablePower(
            BatteryStatus battery,
            Power currentHeatingPower,
            boolean batteryPriorityActive) {

        PowerCalculationContext context = new PowerCalculationContext(battery, currentHeatingPower);
        Power adjustedSurplus = calculateAdjustedSurplus(context);

        Power result;
        if (batteryPriorityActive
                && context.getBatterySoc().isLessThan(config.batteryPriorityThreshold())) {
            result = calculateWithBatteryPriority(adjustedSurplus, context);
        } else {
            result = adjustedSurplus.increase(context.getBatteryPower());
        }

        return applySolarReduction(result).atLeast(Power.ZERO);
    }

    private Power calculateAdjustedSurplus(PowerCalculationContext context) {
        Power consumptionWithoutRod = context.isHeatingActive()
                ? context.getHouseConsumptionPower().reduce(context.getCurrentHeatingPower())
                : context.getHouseConsumptionPower();
        Power surplus = context.getProductionPower().reduce(consumptionWithoutRod);

        if (context.isBatteryCharging()) {
            surplus = surplus.reduce(context.getBatteryPower());
        }
        return surplus.atLeast(Power.ZERO);
    }

    private Power calculateWithBatteryPriority(
            Power adjustedSurplus,
            PowerCalculationContext context) {

        Power available = adjustedSurplus;
        if (context.isBatteryDischarging()) {
            available = available.reduce(context.getBatteryPower().negate());
        }
        return available.reduce(config.batteryReservedPower());
    }

    private Power applySolarReduction(Power power) {
        int reductionPercent = config.solarPowerReductionPercent();
        if (reductionPercent <= 0 || !power.isPositive()) {
            return power;
        }
        long reduction = power.watts() * reductionPercent / 100;
        return power.reduce(reduction);
    }
}
