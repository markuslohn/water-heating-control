package de.bimalo.homeauto.boundary.viessman;

import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.Temperature;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

@Slf4j
@ApplicationScoped
public class VitodensAdapter {

    private final VitodensConfig config;
    private final VitodensModbusClient modbusClient;

    private volatile GasHeatingStatus lastKnownStatus;

    /**
     * Describes the application's intention to keep external hot-water control
     * active. This is not necessarily the actual state reported by the device.
     */
    private volatile boolean heatingRequested;

    @Inject
    public VitodensAdapter(VitodensConfig config) {
        this(config, new VitodensModbusClient(config.modbus().host(), config.modbus().port()));
    }

    VitodensAdapter(VitodensConfig config, VitodensModbusClient modbusClient) {
        this.config = config;
        this.modbusClient = modbusClient;
    }

    /**
     * Initiate the connection to the heating system.
     */
    @PostConstruct
    public void initialize() {
        modbusClient.initialize();
    }

    /**
     * Disconnects from the heating system.
     */
    @PreDestroy
    public synchronized void shutdown() {
        try {
            if (heatingRequested) {
                deactivateHeating();
            }
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to deactivate Vitodens heating during shutdown",
                    ex);
        } finally {
            modbusClient.shutdown();
        }
    }

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @CircuitBreakerName("vitodens-status")
    @Timeout(5000)
    public GasHeatingStatus readStatus() {
        ExternalRequestMode externalRequest = modbusClient.readExternalRequestStatus();

        HotWaterProgram program = modbusClient.readHotWaterHeatingProgramCurrentStatus();

        Temperature currentTemperature = modbusClient.readHotWaterCurrentTemperature();

        Temperature targetTemperature = modbusClient.readHotWaterTargetTemperature();

        boolean active = externalRequest == ExternalRequestMode.MODBUS_CONNECTION
                && program == HotWaterProgram.FLOW_TEMPERATURE_SETPOINT;

        GasHeatingStatus status = GasHeatingStatus.builder().active(active).currentTemperature(currentTemperature)
                .targetTemperature(targetTemperature).measuredAt(Instant.now()).build();

        lastKnownStatus = status;
        return status;
    }

    public Optional<GasHeatingStatus> getLastKnownStatus() {
        return Optional.ofNullable(lastKnownStatus);
    }

    /**
     * Returns whether this application intends to maintain the external
     * hot-water request.
     *
     * <p>
     * This is not the actual device state. Use {@link #readStatus()} to read
     * the state reported by the Vitodens.
     */
    public boolean isHeatingRequested() {
        return heatingRequested;
    }

    /**
     * Activates hot water production via the gas heating: requests external
     * (Modbus) control and sets the hot water program to the flow temperature
     * setpoint. The external request is kept alive periodically until
     * {@link #deactivateHeating()} is called.
     */
    public synchronized void activateHeating() {
        if (heatingRequested) {
            return;
        }
        try {
            modbusClient.writeExternalRequest(ExternalRequestMode.MODBUS_CONNECTION);
            modbusClient.writeHotWaterHeatingProgram(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);
            heatingRequested = true;

            log.info("Vitodens hot-water production activated via external request");
        } catch (RuntimeException activationFailure) {
            heatingRequested = false;

            rollbackFailedActivation(activationFailure);
            throw activationFailure;
        }
    }

    /**
     * Deactivates hot water production via the gas heating: releases the
     * external Modbus request and returns the hot water program to its
     * internal setpoint.
     */
    public synchronized void deactivateHeating() {
        heatingRequested = false;

        RuntimeException failure = null;

        try {
            modbusClient.writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        } catch (RuntimeException ex) {
            failure = ex;
        }

        try {
            modbusClient.writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
        } catch (RuntimeException ex) {
            failure = combineFailures(failure, ex);
        }

        if (failure != null) {
            log.error("Failed to fully deactivate Vitodens hot-water production", failure);
            throw failure;
        }

        log.info("Vitodens external hot-water request deactivated");
    }

    /**
     * Refreshes the external Modbus request while gas heating is active. Runs
     * independently of any control loop so callers only need to call
     * {@link #activateHeating()}/{@link #deactivateHeating()} once.
     */
    @Scheduled(every = "{vitodens.keep-alive-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public synchronized void keepExternalRequestAlive() {
        if (!heatingRequested) {
            return;
        }

        try {
            refreshExternalRequest();
        } catch (RuntimeException ex) {
            log.error("Failed to refresh external Modbus request for gas heating", ex);
        }
    }

    private void refreshExternalRequest() {
        modbusClient.writeExternalRequest(
                ExternalRequestMode.MODBUS_CONNECTION);
    }

    /**
     * Attempts to restore the device to internal control after a partially
     * successful activation.
     */
    private void rollbackFailedActivation(RuntimeException activationFailure) {

        try {
            modbusClient.writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        } catch (RuntimeException rollbackFailure) {
            activationFailure.addSuppressed(rollbackFailure);
        }

        try {
            modbusClient.writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
        } catch (RuntimeException rollbackFailure) {
            activationFailure.addSuppressed(rollbackFailure);
        }

        log.error("Failed to activate Vitodens hot-water production; "
                + "best-effort rollback was executed", activationFailure);
    }

    private RuntimeException combineFailures(
            RuntimeException first,
            RuntimeException additional) {

        if (first == null) {
            return additional;
        }

        first.addSuppressed(additional);
        return first;
    }
}
