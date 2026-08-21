package de.bimalo.homeauto.boundary.elwa2;

import de.bimalo.homeauto.boundary.modbus.ModbusClientException;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.Power;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
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

    private volatile HeatingRodStatus lastKnownStatus;

    // Tracks the currently requested heating power, used for idempotency
    // (avoid redundant writes of the same value).
    private volatile Power lastRequestedPower = Power.ZERO;

    // Whether a power command has been sent since this instance started; lets
    // stopHeating() skip a redundant zero-write instead of relying purely on
    // lastRequestedPower, which defaults to zero regardless of the actual device
    // state on startup.
    private volatile boolean powerRequestSent = false;

    @Inject
    public Elwa2Adapter(Elwa2Config config) {
        this(config, new Elwa2ModbusClient(config.modbus().host(), config.modbus().port()));
    }

    Elwa2Adapter(Elwa2Config config, Elwa2ModbusClient modbusClient) {
        this.modbusClient = modbusClient;
    }

    @PostConstruct
    public void initialize() {
        modbusClient.initialize();
    }

    @PreDestroy
    public void shutdown() {
        try {
            stopHeating();
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to deactivate ELWA2 heating during shutdown",
                    ex);
        } finally {
            modbusClient.shutdown();
        }
    }

    @Retry(maxRetries = 2, delay = 200, retryOn = ModbusClientException.class, abortOn = IllegalArgumentException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(6000)
    public HeatingRodStatus readStatus() {
        HeatingRodStatus status = new HeatingRodStatus(
                modbusClient.readTemperature1(),
                modbusClient.readTargetTemperature(),
                modbusClient.readPower(),
                Instant.now());

        lastKnownStatus = status;
        return status;
    }

    public Optional<HeatingRodStatus> getLastKnownStatus() {
        return Optional.ofNullable(lastKnownStatus);
    }

    public boolean isConnected() {
        return modbusClient.isConnected();
    }

    public synchronized void stopHeating() {
        Power previousPower = lastRequestedPower;

        if (powerRequestSent && previousPower.equals(Power.ZERO)) {
            return;
        }

        try {
            modbusClient.setPower(Power.ZERO);
            lastRequestedPower = Power.ZERO;
            powerRequestSent = true;

            if (previousPower.isPositive()) {
                log.info("Heating stopped; previous request was {}", previousPower);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to send stop command to ELWA2", ex);
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
        powerRequestSent = true;

        if (!power.equals(previousPower)) {
            log.info("Heating power changed from {} to {}", previousPower, power);
        }
    }
}
