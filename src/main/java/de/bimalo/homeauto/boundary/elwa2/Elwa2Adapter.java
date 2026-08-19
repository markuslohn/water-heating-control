package de.bimalo.homeauto.boundary.elwa2;

import de.bimalo.homeauto.boundary.modbus.ModbusClientException;
import de.bimalo.homeauto.entity.Power;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Adapter for the ELWA2 heating rod.
 * Provides access to key performance data of the heating rod.
 * Implements Circuit Breaker pattern to handle Modbus communication failures.
 */
@Slf4j
@ApplicationScoped
public class Elwa2Adapter {

    private final Elwa2ModbusClient modbusClient;
    private Elwa2Config config;

    private volatile Elwa2Measurements lastKnownMeasurements;

    private volatile Duration powerCommandTimeout;

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
        powerCommandTimeout = determinePowerCommandTimeout();
    }

    @PreDestroy
    public void shutdown() {
        modbusClient.shutdown();
    }

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(6000)
    public Elwa2Measurements readMeasurements() {
        Elwa2Measurements measurements = new Elwa2Measurements(
                modbusClient.readTemperature1(),
                modbusClient.readTargetTemperature(),
                modbusClient.readPower(),
                modbusClient.readStatus(),
                Instant.now());

        lastKnownMeasurements = measurements;
        return measurements;
    }

    public Optional<Elwa2Measurements> getLastKnownMeasurements() {
        return Optional.ofNullable(lastKnownMeasurements);
    }

    public synchronized void stopHeating() {
        Power previousPower = lastRequestedPower;

        // before modus write deactivate keep-alive
        lastRequestedPower = Power.ZERO;

        try {
            modbusClient.setPower(Power.ZERO);
            lastPowerRequestAt = Instant.now();

            if (previousPower.isPositive()) {
                log.info("Heating stopped; previous request was {}", previousPower);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to send stop command to ELWA2; keep-alive remains disabled", ex);
            throw ex;
        }
    }

    /**
     * Sets the heating power of the ELWA2 heating rod.
     *
     * @param power the desired heating power
     * @throws NullPointerException     if power is null
     * @throws IllegalArgumentException if the power is negative or exceeds the
     *                                  maximum power
     */
    public synchronized void adjustHeating(Power power) {
        Objects.requireNonNull(power, "Power must not be null");

        if (!power.isPositive()) {
            stopHeating();
            return;
        }

        Power previousPower = lastRequestedPower;

        modbusClient.setPower(power);
        lastRequestedPower = power;
        lastPowerRequestAt = Instant.now();

        if (!power.equals(previousPower)) {
            log.info("Heating power changed from {} to {}", previousPower, power);
        }
    }

    /**
     * Refreshes the heating power request before the ELWA2's own power timeout
     * (see {@link #readPowerTimeout()}) elapses; otherwise the ELWA2 reverts to
     * standby on its own. Runs independently of any control loop so callers only
     * need to call {@link #adjustHeating(Power)} once. Does nothing while no
     * heating power is requested.
     */
    @Scheduled(every = "{elwa2.keep-alive-check-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public synchronized void keepHeatingAlive() {
        if (!lastRequestedPower.isPositive()) {
            return;
        }
        try {
            Duration elapsed = Duration.between(lastPowerRequestAt, Instant.now());
            if (elapsed.compareTo(powerCommandTimeout.dividedBy(2)) >= 0) {
                adjustHeating(lastRequestedPower);
            }
        } catch (Exception e) {
            log.error("Failed to refresh heating power request for ELWA2", e);
        }
    }

    private Duration determinePowerCommandTimeout() {
        try {
            Duration timeout = modbusClient.readPowerCommandTimeout();
            validatePowerCommandTimeout(timeout);

            log.info("ELWA2 power command timeout is {}", timeout);
            return timeout;
        } catch (ModbusClientException e) {
            Duration fallback = config.powerCommandTimeoutFallback();

            log.warn(
                    "Could not read ELWA2 power command timeout; using configured fallback {}",
                    fallback,
                    e);

            return fallback;
        }
    }

    private void validatePowerCommandTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "ELWA2 power command timeout must be positive");
        }

        if (config.keepAliveCheckInterval().compareTo(timeout.dividedBy(2)) >= 0) {
            throw new IllegalStateException(
                    "ELWA2 keep-alive interval must be shorter than half "
                            + "the power command timeout");
        }
    }
}
