package de.bimalo.homeauto.boundary.viessman;

import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.Volume;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;

@Slf4j
@ApplicationScoped
public class VitodensAdapter {

    private final VitodensConfig config;
    private final VitodensModbusClient modbusClient;

    // Cached values for fallback when circuit is open
    private volatile Temperature lastKnownHotWaterCurrentTemperature = Temperature.ofCelsius(50.0);
    private volatile Temperature lastKnownOutsideTemperature = Temperature.ofCelsius(10.0);
    private volatile Temperature lastKnownHotWaterTargetTemperature = Temperature.ofCelsius(50.0);
    private volatile Volume lastKnownHotWaterGasConsumptionToday = Volume.ZERO;
    private volatile Volume lastKnownHotWaterGasConsumptionThisMonth = Volume.ZERO;

    // Tracks whether this application currently intends to drive hot water
    // production via the external Modbus request (drives the keep-alive schedule).
    private final AtomicBoolean active = new AtomicBoolean(false);

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
    public void shutdown() {
        modbusClient.shutdown();
    }

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readHotWaterCurrentTemperatureFallback")
    @CircuitBreakerName("vitodens-hotwater-current-temperature")
    public Temperature readHotWaterCurrentTemperature() {
        Temperature result = modbusClient.readHotWaterCurrentTemperature();
        lastKnownHotWaterCurrentTemperature = result; // Cache successful read
        return result;
    }

    private Temperature readHotWaterCurrentTemperatureFallback() {
        log.warn("Circuit breaker open for hot water current temperature read, returning last known value: {}°C",
                lastKnownHotWaterCurrentTemperature.getCelsius());
        return lastKnownHotWaterCurrentTemperature;
    }

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readOutsideTemperatureFallback")
    @CircuitBreakerName("vitodens-outside-temperature")
    public Temperature readOutsideTemperature() {
        Temperature result = modbusClient.readOutsideTemperature();
        lastKnownOutsideTemperature = result; // Cache successful read
        return result;
    }

    private Temperature readOutsideTemperatureFallback() {
        log.warn("Circuit breaker open for outside temperature read, returning last known value: {}°C",
                lastKnownOutsideTemperature.getCelsius());
        return lastKnownOutsideTemperature;
    }

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readHotWaterTargetTemperatureFallback")
    @CircuitBreakerName("vitodens-hotwater-target-temperature")
    public Temperature readHotWaterTargetTemperature() {
        Temperature result = modbusClient.readHotWaterTargetTemperature();
        lastKnownHotWaterTargetTemperature = result; // Cache successful read
        return result;
    }

    private Temperature readHotWaterTargetTemperatureFallback() {
        log.warn("Circuit breaker open for hot water target temperature read, returning last known value: {}°C",
                lastKnownHotWaterTargetTemperature.getCelsius());
        return lastKnownHotWaterTargetTemperature;
    }

    /**
     * Activates hot water production via the gas heating: requests external
     * (Modbus) control and sets the hot water program to the flow temperature
     * setpoint. The external request is kept alive periodically until
     * {@link #deactivateHeating()} is called.
     */
    public void activateHeating() {
        continueHeating();
        modbusClient.writeHotWaterHeatingProgram(HotWaterProgram.FLOW_TEMPERATURE_SETPOINT);
        active.set(true);
        log.info("Gas heating activated for hot water production");
    }

    /**
     * Deactivates hot water production via the gas heating: releases the
     * external Modbus request and returns the hot water program to its
     * internal setpoint.
     */
    public void deactivateHeating() {
        modbusClient.writeHotWaterHeatingProgram(HotWaterProgram.INTERNAL_SHOULD_VALUE);
        modbusClient.writeExternalRequest(ExternalRequestMode.NO_CONNECTION);
        active.set(false);
        log.info("Gas heating deactivated");
    }

    /**
     * Refreshes the external Modbus request. The Vitodens falls back to internal
     * control if this register isn't refreshed periodically, so this must be
     * called cyclically while heating is active.
     */
    public void continueHeating() {
        modbusClient.writeExternalRequest(ExternalRequestMode.MODBUS_CONNECTION);
        active.set(true);
    }

    /**
     * Refreshes the external Modbus request while gas heating is active. Runs
     * independently of any control loop so callers only need to call
     * {@link #activateHeating()}/{@link #deactivateHeating()} once.
     */
    @Scheduled(every = "{vitodens.keep-alive-interval}")
    public void keepExternalRequestAlive() {
        System.out.println("keep " + active.get());
        if (!active.get()) {
            return;
        }
        try {
            continueHeating();
        } catch (Exception e) {
            log.error("Failed to refresh external Modbus request for gas heating", e);
        }
    }

    /**
     * Returns whether hot water is currently being produced via the gas heating,
     * based on the actual device state rather than just this application's intent.
     */
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "isHeatingActiveFallback")
    @CircuitBreakerName("vitodens-external-request-status")
    public boolean isHeatingActive() {
        return modbusClient.readExternalRequestStatus() == ExternalRequestMode.MODBUS_CONNECTION
                && modbusClient.readHotWaterHeatingProgramCurrentStatus() == HotWaterProgram.FLOW_TEMPERATURE_SETPOINT;
    }

    private boolean isHeatingActiveFallback() {
        log.warn("Circuit breaker open for external request status read, returning last known intent: {}",
                active.get());
        return active.get();
    }

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readHotWaterGasConsumptionTodayFallback")
    @CircuitBreakerName("vitodens-gas-consumption-today")
    public Volume readHotWaterGasConsumptionToday() {
        Volume result = modbusClient.readHotWaterGasConsumptionToday();
        lastKnownHotWaterGasConsumptionToday = result; // Cache successful read
        return result;
    }

    private Volume readHotWaterGasConsumptionTodayFallback() {
        log.warn("Circuit breaker open for gas consumption (today) read, returning last known value: {}",
                lastKnownHotWaterGasConsumptionToday);
        return lastKnownHotWaterGasConsumptionToday;
    }

    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(value = 3000)
    @Fallback(fallbackMethod = "readHotWaterGasConsumptionThisMonthFallback")
    @CircuitBreakerName("vitodens-gas-consumption-month")
    public Volume readHotWaterGasConsumptionThisMonth() {
        Volume result = modbusClient.readHotWaterGasConsumptionThisMonth();
        lastKnownHotWaterGasConsumptionThisMonth = result; // Cache successful read
        return result;
    }

    private Volume readHotWaterGasConsumptionThisMonthFallback() {
        log.warn("Circuit breaker open for gas consumption (this month) read, returning last known value: {}",
                lastKnownHotWaterGasConsumptionThisMonth);
        return lastKnownHotWaterGasConsumptionThisMonth;
    }
}
