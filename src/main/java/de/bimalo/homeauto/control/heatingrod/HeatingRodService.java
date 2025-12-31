package de.bimalo.homeauto.control.heatingrod;

import de.bimalo.homeauto.boundary.elwa2.Elwa2ModbusClient;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Service class for the ELWA2 heating rod.
 * Provides access to key performance data of the heating rod.
 *
 */
@Slf4j
@ApplicationScoped
public class HeatingRodService {

    private final Elwa2ModbusClient modbusClient;
    private final HeatingRodConfig config;

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

    public Temperature readTemperature1() {
        double rawValue = modbusClient.readTemperature1();
        return Temperature.ofCelsius(rawValue);
    }

    public Temperature readTargetTemperature() {
        double rawValue = modbusClient.readTargetTemperature();
        return Temperature.ofCelsius(rawValue);
    }

    public String readDeviceStatus() {
        return modbusClient.readStatus().toString();
    }

    public Power readPower() {
        int rawValue = modbusClient.readPower();
        return Power.ofWatts(rawValue);
    }

    public Power readMaxPower() {
        int rawValue = modbusClient.readMaxPower();
        return Power.ofWatts(rawValue);
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
