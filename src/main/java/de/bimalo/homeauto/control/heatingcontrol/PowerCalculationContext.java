package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import lombok.Getter;

/**
 * Aggregates all power-related values needed for heating control calculations.
 * Provides a single source of truth for power values during a control cycle.
 */
@Getter
public class PowerCalculationContext {

    private final Power productionPower;
    private final Power houseConsumptionPower;
    private final Power batteryPower;
    private final Power currentHeatingPower;
    private final Percentage batterySoc;

    public PowerCalculationContext(BatteryStatus batteryStatus, Power currentHeatingPower) {
        this.productionPower = batteryStatus.productionPower();
        this.houseConsumptionPower = batteryStatus.consumptionPower();
        this.batteryPower = batteryStatus.batteryPower();
        this.batterySoc = batteryStatus.batteryStateOfCharge();
        this.currentHeatingPower = currentHeatingPower;
    }

    public boolean isBatteryCharging() {
        return batteryPower.isPositive();
    }

    public boolean isBatteryDischarging() {
        return batteryPower.isNegative();
    }

    public boolean isHeatingActive() {
        return currentHeatingPower.isPositive();
    }
}
