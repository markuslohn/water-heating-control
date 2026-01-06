package de.bimalo.homeauto.control.battery;

import de.bimalo.homeauto.boundary.e3dc.E3dcModbusClient;
import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Service class for the E3/DC battery storage in Simple Mode.
 * Provides access to key performance data of the battery storage.
 * Implements Circuit Breaker pattern to handle Modbus communication failures.
 */
@Slf4j
@ApplicationScoped
public class BatteryStorageService {

    private final E3dcModbusClient modbusClient;
    private final BatteryStorageConfig config;

    // Cached values for fallback when circuit is open
    private volatile Power lastKnownGridPower = Power.ofWatts(0);
    private volatile Power lastKnownBatteryPower = Power.ofWatts(0);
    private volatile Power lastKnownProductionPower = Power.ofWatts(0);
    private volatile Power lastKnownConsumptionPower = Power.ofWatts(1000);
    private volatile Percentage lastKnownBatteryStateOfCharge = Percentage.of(50);

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

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readGridPowerFallback")
    @CircuitBreakerName("e3dc-grid-power")
    Power readGridPower() {
        long rawValue = modbusClient.readGridPower();
        Power result = Power.ofWatts(rawValue);
        lastKnownGridPower = result;
        return result;
    }

    Power readGridPowerFallback() {
        log.warn("Circuit breaker open for grid power read, returning last known value: {} W",
                lastKnownGridPower.getWatts());
        return lastKnownGridPower;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readBatteryPowerFallback")
    @CircuitBreakerName("e3dc-battery-power")
    Power readBatteryPower() {
        long rawValue = modbusClient.readBatteryPower();
        Power result = Power.ofWatts(rawValue);
        lastKnownBatteryPower = result;
        return result;
    }

    Power readBatteryPowerFallback() {
        log.warn("Circuit breaker open for battery power read, returning last known value: {} W",
                lastKnownBatteryPower.getWatts());
        return lastKnownBatteryPower;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readProductionPowerFallback")
    @CircuitBreakerName("e3dc-production-power")
    Power readProductionPower() {
        long rawValue = modbusClient.readProductionPower();
        Power result = Power.ofWatts(rawValue);
        lastKnownProductionPower = result;
        return result;
    }

    Power readProductionPowerFallback() {
        log.warn("Circuit breaker open for production power read, returning last known value: {} W",
                lastKnownProductionPower.getWatts());
        return lastKnownProductionPower;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readHouseConsumptionPowerFallback")
    @CircuitBreakerName("e3dc-consumption-power")
    Power readHouseConsumptionPower() {
        long rawValue = modbusClient.readHouseConsumptionPower();
        Power result = Power.ofWatts(rawValue);
        lastKnownConsumptionPower = result;
        return result;
    }

    Power readHouseConsumptionPowerFallback() {
        log.warn("Circuit breaker open for consumption power read, returning last known value: {} W",
                lastKnownConsumptionPower.getWatts());
        return lastKnownConsumptionPower;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readBatteryStateOfChargeFallback")
    @CircuitBreakerName("e3dc-battery-soc")
    Percentage readBatteryStateOfCharge() {
        int rawValue = modbusClient.readBatteryStateOfCharge();
        Percentage result = Percentage.of(rawValue);
        lastKnownBatteryStateOfCharge = result;
        return result;
    }

    Percentage readBatteryStateOfChargeFallback() {
        log.warn("Circuit breaker open for battery SOC read, returning last known value: {}%",
                lastKnownBatteryStateOfCharge.getValue());
        return lastKnownBatteryStateOfCharge;
    }

}
