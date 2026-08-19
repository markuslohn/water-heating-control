package de.bimalo.homeauto.boundary.goecharger;

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
 * Service class for the go-eCharger wallbox.
 * Provides access to car charging status and charging power.
 * Implements Circuit Breaker pattern to handle Modbus communication failures.
 */
@Slf4j
@ApplicationScoped
public class GoEchargerAdapter {

    private final GoEchargerModbusClient modbusClient;
    private final GoEchargerConfig config;

    // Cached values for fallback when circuit is open
    private volatile CarStatus lastKnownCarStatus = CarStatus.UNKNOWN;
    private volatile Power lastKnownChargingPower = Power.ofWatts(0);

    @Inject
    public GoEchargerAdapter(GoEchargerConfig config) {
        this.config = config;
        this.modbusClient = new GoEchargerModbusClient(config.modbus().host(), config.modbus().port());
    }

    @PostConstruct
    public void initialize() {
        modbusClient.initialize();
    }

    @PreDestroy
    public void shutdown() {
        modbusClient.shutdown();
    }

    public boolean isCharging() {
        return readCarStatus() == CarStatus.CHARGING;
    }

    public Power readCurrentChargingPower() {
        return readChargingPower();
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readCarStatusFallback")
    @CircuitBreakerName("goecharger-car-status")
    CarStatus readCarStatus() {
        CarStatus result = modbusClient.readCarStatus();
        lastKnownCarStatus = result;
        return result;
    }

    CarStatus readCarStatusFallback() {
        log.warn("Circuit breaker open for car status read, returning last known value: {}", lastKnownCarStatus);
        return lastKnownCarStatus;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readChargingPowerFallback")
    @CircuitBreakerName("goecharger-charging-power")
    Power readChargingPower() {
        Power powerL1 = modbusClient.readPowerL1();
        Power powerL2 = modbusClient.readPowerL2();
        Power powerL3 = modbusClient.readPowerL3();
        Power result = powerL1.increase(powerL2).increase(powerL3);
        lastKnownChargingPower = result;
        return result;
    }

    Power readChargingPowerFallback() {
        log.warn("Circuit breaker open for charging power read, returning last known value: {} W",
                lastKnownChargingPower.getWatts());
        return lastKnownChargingPower;
    }

}
