package de.bimalo.homeauto.control.wallbox;

import de.bimalo.homeauto.boundary.goecharger.CarStatus;
import de.bimalo.homeauto.boundary.goecharger.GoEchargerModbusClient;
import de.bimalo.homeauto.entity.Power;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class WallboxService {

    private final GoEchargerModbusClient modbusClient;
    private final WallboxConfig config;

    @Inject
    public WallboxService(WallboxConfig config) {
        this.config = config;
        this.modbusClient = new GoEchargerModbusClient(config.modbus().host(), config.modbus().port());
    }

    @PostConstruct
    public void initialize() {
        modbusClient.initialize();
    }

    @PreDestroy
    public void shutdown() {
        modbusClient.shutdown();
    }

    public boolean isCharging() {
        return modbusClient.readCarStatus() == CarStatus.CHARGING;
    }

    public Power readCurrentChargingPower() {
        Power powerL1 = modbusClient.readPowerL1();
        Power powerL2 = modbusClient.readPowerL2();
        Power powerL3 = modbusClient.readPowerL3();
        return powerL1.increase(powerL2).increase(powerL3);
    }

}