package de.bimalo.homeauto.boundary.e3dc;

import de.bimalo.homeauto.entity.BatteryStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Service class for the E3/DC battery storage in Simple Mode.
 * Provides access to key performance data of the battery storage.
 * Implements Circuit Breaker pattern to handle Modbus communication failures.
 */
@Slf4j
@ApplicationScoped
public class E3dcAdapter {

    private final E3dcModbusClient modbusClient;

    private volatile BatteryStatus lastKnownStatus;

    @Inject
    public E3dcAdapter(E3dcConfig config) {
        this(config, new E3dcModbusClient(config.modbus().host(), config.modbus().port()));
    }

    public E3dcAdapter(E3dcConfig config, E3dcModbusClient modbusClient) {
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

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(6000)
    public BatteryStatus readStatus() {
        BatteryStatus status = BatteryStatus.builder()
                .measuredAt(Instant.now())
                .productionPower(modbusClient.readProductionPower())
                .consumptionPower(modbusClient.readHouseConsumptionPower())
                .batteryPower(modbusClient.readBatteryPower())
                .gridPower(modbusClient.readGridPower())
                .batteryStateOfCharge(
                        modbusClient.readBatteryStateOfCharge())
                .build();

        lastKnownStatus = status;
        return status;
    }

    public Optional<BatteryStatus> getLastKnownStatus() {
        return Optional.ofNullable(lastKnownStatus);
    }

}
