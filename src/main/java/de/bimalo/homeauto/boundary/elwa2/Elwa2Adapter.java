package de.bimalo.homeauto.boundary.elwa2;

import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
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
public class Elwa2Adapter {

    private static final Duration DEFAULT_POWER_TIMEOUT = Duration.ofMinutes(1);

    private final Elwa2ModbusClient modbusClient;
    private final Elwa2Config config;

    // Cached values for fallback when circuit is open
    private volatile Temperature lastKnownTemperature1 = Temperature.ofCelsius(20.0);
    private volatile Temperature lastKnownTargetTemperature = Temperature.ofCelsius(60.0);
    private volatile Power lastKnownPower = Power.ofWatts(0);
    private volatile Power lastKnownMaxPower = Power.ofWatts(Elwa2ModbusClient.MAX_POWER_WATTS);
    private volatile Elwa2Status lastKnownStatus = Elwa2Status.UNKNOWN;

    // Tracks the currently requested heating power and when it was last written,
    // used to keep the request alive within the ELWA2's own power timeout.
    private volatile Power lastRequestedPower = Power.ZERO;
    private volatile Instant lastPowerRequestAt = Instant.EPOCH;

    @Inject
    public Elwa2Adapter(Elwa2Config config) {
        this(config, new Elwa2ModbusClient(config.modbus().host(), config.modbus().port()));
    }

    Elwa2Adapter(Elwa2Config config, Elwa2ModbusClient modbusClient) {
        this.config = config;
        this.modbusClient = modbusClient;
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
        Temperature result = modbusClient.readTemperature1();
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
        Temperature result = modbusClient.readTargetTemperature();
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
    public Elwa2Status readDeviceStatus() {
        Elwa2Status result = modbusClient.readStatus();
        lastKnownStatus = result; // Cache successful read
        return result;
    }

    private Elwa2Status readDeviceStatusFallback() {
        log.warn("Circuit breaker open for device status read, returning last known value: {}", lastKnownStatus);
        return lastKnownStatus;
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
        Power result = modbusClient.readPower();
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
        Power result = modbusClient.readMaxPower();
        lastKnownMaxPower = result; // Cache successful read
        return result;
    }

    private Power readMaxPowerFallback() {
        log.warn("Circuit breaker open for max power read, returning last known value: {} W",
                lastKnownMaxPower.getWatts());
        return lastKnownMaxPower;
    }

    @CircuitBreaker(
            requestVolumeThreshold = 4,
            failureRatio = 0.5,
            delay = 5000,
            successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readPowerTimeoutFallback")
    @CircuitBreakerName("elwa2-power-timeout")
    public Duration readPowerTimeout() {
        return modbusClient.readPowerTimeout();
    }

    private Duration readPowerTimeoutFallback(Exception e) {
        log.error("Failed to read power timeout, returning default value of {}", DEFAULT_POWER_TIMEOUT, e);
        return DEFAULT_POWER_TIMEOUT;
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
        boolean changed = !power.equals(lastRequestedPower);
        modbusClient.setPower(power);
        if (changed) {
            log.info("Heating power changed from {} to {}", lastRequestedPower, power);
        }
        lastRequestedPower = power;
        lastPowerRequestAt = Instant.now();
    }

    /**
     * Refreshes the heating power request before the ELWA2's own power timeout
     * (see {@link #readPowerTimeout()}) elapses; otherwise the ELWA2 reverts to
     * standby on its own. Runs independently of any control loop so callers only
     * need to call {@link #adjustHeating(Power)} once. Does nothing while no
     * heating power is requested.
     */
    @Scheduled(every = "{elwa2.keep-alive-check-interval}")
    public void keepHeatingAlive() {
        if (!lastRequestedPower.isPositive()) {
            return;
        }
        try {
            Duration timeout = readPowerTimeout();
            Duration elapsed = Duration.between(lastPowerRequestAt, Instant.now());
            if (elapsed.compareTo(timeout.dividedBy(2)) >= 0) {
                adjustHeating(lastRequestedPower);
            }
        } catch (Exception e) {
            log.error("Failed to refresh heating power request for ELWA2", e);
        }
    }
}
