package de.bimalo.homeauto.control.battery;

import de.bimalo.homeauto.boundary.e3dc.E3dcModbusClient;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Service class for the E3/DC battery storage in Simple Mode.
 * Provides access to key performance data of the battery storage.
 */
@Slf4j
@ApplicationScoped
public class BatteryStorageService {

    private final E3dcModbusClient modbusClient;
    private final BatteryStorageConfig config;

    @Inject
    public BatteryStorageService(BatteryStorageConfig config) {
        this.config = config;
        this.modbusClient = new E3dcModbusClient(config.modbus().host(), config.modbus().port());
    }

    @PostConstruct
    public void initialize() {
        modbusClient.initialize();
    }

    @PreDestroy
    public void shutdown() {
        modbusClient.shutdown();
    }

    public BatteryStatus getCurrentStatus() {
        return BatteryStatus.builder()
                .timestamp(java.time.LocalDateTime.now())
                .productionPower(readProductionPower())
                .consumptionPower(readHouseConsumptionPower())
                .batteryPower(readBatteryPower())
                .gridPower(readGridPower())
                .batteryStateOfCharge(readBatteryStateOfCharge())
                .build();
    }

    /**
     * Determines the pure solar power surplus available.
     * Only counts actual solar production, not battery discharge.
     *
     * @return Power surplus from solar only (battery discharge is not counted)
     */
    public Power determineSolarPowerSurplus() {
        Power productionPower = readProductionPower();
        Power houseConsumptionPower = readHouseConsumptionPower();
        Power batteryPower = readBatteryPower();

        // Base solar surplus: Production - Consumption
        Power surplusPower = productionPower.subtract(houseConsumptionPower);

        // If battery is charging, this solar power is not available for other use
        if (batteryPower.isPositive()) {
            surplusPower = surplusPower.subtract(batteryPower);
        }
        // If battery is discharging (negative), we ignore it - it's not solar power

        if (surplusPower.isNegative()) {
            return Power.ofWatts(0);
        } else {
            return surplusPower;
        }
    }

    private Power readGridPower() {
        long rawValue = modbusClient.readGridPower();
        return Power.ofWatts(rawValue);
    }

    private Power readBatteryPower() {
        long rawValue = modbusClient.readBatteryPower();
        return Power.ofWatts(rawValue);
    }

    private Power readProductionPower() {
        long rawValue = modbusClient.readProductionPower();
        return Power.ofWatts(rawValue);
    }

    private Power readHouseConsumptionPower() {
        long rawValue = modbusClient.readHouseConsumptionPower();
        return Power.ofWatts(rawValue);
    }

    private Percentage readBatteryStateOfCharge() {
        int rawValue = modbusClient.readBatteryStateOfCharge();
        return Percentage.of(rawValue);
    }

}
