package de.bimalo.homeauto.boundary.goecharger;

import de.bimalo.homeauto.boundary.modbus.ModbusClientException;
import de.bimalo.homeauto.entity.CarStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.WallboxStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
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

    private volatile WallboxStatus lastKnownStatus;

    @Inject
    public GoEchargerAdapter(GoEchargerConfig config) {
        this(config, new GoEchargerModbusClient(config.modbus().host(), config.modbus().port()));
    }

    GoEchargerAdapter(GoEchargerConfig config, GoEchargerModbusClient modbusClient) {
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

    @Retry(maxRetries = 2, delay = 200, retryOn = ModbusClientException.class, abortOn = IllegalArgumentException.class)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(5000)
    public WallboxStatus readStatus() {
        CarStatus carStatus = modbusClient.readCarStatus();

        Power chargingPower = modbusClient.readPowerL1()
                .increase(modbusClient.readPowerL2())
                .increase(modbusClient.readPowerL3());

        WallboxStatus status = WallboxStatus.builder().chargingPower(chargingPower).measuredAt(Instant.now())
                .operatingStatus(carStatus).build();
        lastKnownStatus = status;
        return status;
    }

    public Optional<WallboxStatus> getLastKnownStatus() {
        return Optional.ofNullable(lastKnownStatus);
    }

    public boolean isConnected() {
        return modbusClient.isConnected();
    }

}
