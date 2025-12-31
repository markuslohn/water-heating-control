package de.bimalo.homeauto.control.heating;

import de.bimalo.homeauto.boundary.viessman.VitodensModbusClient;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Temperature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class HeatingService {

    private final HeatingConfig config;
    private final VitodensModbusClient modbusClient;

    @Inject
    public HeatingService(HeatingConfig config) {
        this.config = config;
        this.modbusClient = new VitodensModbusClient(config.modbus().host(), config.modbus().port());
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

    public Temperature readHotWaterTemperature() {
        double rawValue = modbusClient.readHotWaterTemperature();
        return Temperature.ofCelsius(rawValue);
    }

    public Temperature readOutsideTemperature() {
        double rawValue = modbusClient.readOutsideTemperature();
        return Temperature.ofCelsius(rawValue);
    }

    public int readHotWaterStatus() {
        return modbusClient.readHotWaterStatus();
    }

    public int readBetriebsstundenWaermeerzeuger() {
        return modbusClient.readBetriebsstundenWaermeerzeuger();
    }

    public Percentage readXXX() {
        int rawValue = modbusClient.readProzentwert();
        return Percentage.of(rawValue);
    }
}
