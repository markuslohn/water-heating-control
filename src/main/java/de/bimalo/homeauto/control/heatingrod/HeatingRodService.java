package de.bimalo.homeauto.control.heatingrod;

import de.bimalo.homeauto.boundary.elwa2.Elwa2ModbusClient;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Service class for the ELWA2 heating rod.
 * Provides access to key performance data of the heating rod.
 * Implements Circuit Breaker pattern to handle Modbus communication failures.
 */
@Slf4j
@ApplicationScoped
public class HeatingRodService {

    private final Elwa2ModbusClient modbusClient;
    private final HeatingRodConfig config;

    // Cached values for fallback when circuit is open
    private volatile Temperature lastKnownTemperature1 = Temperature.ofCelsius(20.0);
    private volatile Temperature lastKnownTargetTemperature = Temperature.ofCelsius(60.0);
    private volatile Power lastKnownPower = Power.ofWatts(0);
    private volatile Power lastKnownMaxPower = Power.ofWatts(3000);

    @Inject
    public HeatingRodService(HeatingRodConfig config) {
        this.config = config;
        this.modbusClient = new Elwa2ModbusClient(config.modbus().host(), config.modbus().port());
    }

    @PostConstruct
    public void initialize() {
        modbusClient.initialize();
    }

    @PreDestroy
    public void shutdown() {
        modbusClient.shutdown();
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readTemperature1Fallback")
    @CircuitBreakerName("elwa2-temperature1")
    public Temperature readTemperature1() {
        double rawValue = modbusClient.readTemperature1();
        Temperature result = Temperature.ofCelsius(rawValue);
        lastKnownTemperature1 = result; // Cache successful read
        return result;
    }

    private Temperature readTemperature1Fallback() {
        log.warn("Circuit breaker open for temperature1 read, returning last known value: {}°C",
                lastKnownTemperature1.getCelsius());
        return lastKnownTemperature1;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readTargetTemperatureFallback")
    @CircuitBreakerName("elwa2-target-temperature")
    public Temperature readTargetTemperature() {
        double rawValue = modbusClient.readTargetTemperature();
        Temperature result = Temperature.ofCelsius(rawValue);
        lastKnownTargetTemperature = result; // Cache successful read
        return result;
    }

    private Temperature readTargetTemperatureFallback() {
        log.warn("Circuit breaker open for target temperature read, returning last known value: {}°C",
                lastKnownTargetTemperature.getCelsius());
        return lastKnownTargetTemperature;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readDeviceStatusFallback")
    @CircuitBreakerName("elwa2-status")
    public String readDeviceStatus() {
        return modbusClient.readStatus().toString();
    }

    private String readDeviceStatusFallback() {
        log.warn("Circuit breaker open for device status read, returning 'UNKNOWN'");
        return "UNKNOWN";
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readPowerFallback")
    @CircuitBreakerName("elwa2-power")
    public Power readPower() {
        int rawValue = modbusClient.readPower();
        Power result = Power.ofWatts(rawValue);
        lastKnownPower = result; // Cache successful read
        return result;
    }

    private Power readPowerFallback() {
        log.warn("Circuit breaker open for power read, returning last known value: {} W",
                lastKnownPower.getWatts());
        return lastKnownPower;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readMaxPowerFallback")
    @CircuitBreakerName("elwa2-max-power")
    public Power readMaxPower() {
        int rawValue = modbusClient.readMaxPower();
        Power result = Power.ofWatts(rawValue);
        lastKnownMaxPower = result; // Cache successful read
        return result;
    }

    private Power readMaxPowerFallback() {
        log.warn("Circuit breaker open for max power read, returning last known value: {} W",
                lastKnownMaxPower.getWatts());
        return lastKnownMaxPower;
    }

    public int readPowerTimeout() {
        return modbusClient.readPowerTimeout();
    }

    /**
     * Sets the heating power of the ELWA2 heating rod.
     *
     * @param power the desired heating power
     * @throws NullPointerException if power is null
     * @throws IllegalArgumentException if the power is negative or exceeds the
     *                                  maximum power
     */
    public void adjustHeating(Power power) {
        Objects.requireNonNull(power, "Power must not be null");
        long watts = power.getWatts();
        modbusClient.setPower((int) watts);
    }
}
