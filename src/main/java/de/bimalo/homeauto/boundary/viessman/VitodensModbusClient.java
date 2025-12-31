package de.bimalo.homeauto.boundary.viessman;

import de.bimalo.homeauto.boundary.modbus.AbstractModbusClient;
import de.bimalo.homeauto.entity.DeviceInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class VitodensModbusClient extends AbstractModbusClient {

    public VitodensModbusClient(String host, int port) {
        super(host, port);
    }

    @Override
    protected DeviceInfo readDeviceInfo() {
        return DeviceInfo.builder()
                .manufacturer("Viessmann")
                .model("Vitodens 300-W")
                .serialNumber("7956286309946129")
                .firmwareVersion("1.0.0")
                .build();
    }

    public double readHotWaterTemperature() {
        VitodensRegister register = VitodensRegister.HOT_WATER_TEMPERATURE;
        int rawValue = this.readInputUnsignedInteger(register.getAddress());
        return this.readInputInteger(register.getAddress()) / register.getScaleFactor();
    }

    public double readOutsideTemperature() {
        VitodensRegister register = VitodensRegister.OUTSIDE_TEMPERATURE;
        return this.readInputInteger(register.getAddress()) / register.getScaleFactor();
    }

    public int readHotWaterStatus() {
        VitodensRegister register = VitodensRegister.HOT_WATER_STATUS;
        return this.readInputUnsignedInteger(register.getAddress());
    }

    public int readBetriebsstundenWaermeerzeuger() {
        return this.readInputUnsignedInteger(VitodensRegister.BETRIEBSSTUNDEN_WARMEERZEUGER.getAddress());
    }

    public int readProzentwert() {
        return this.readInputUnsignedInteger(VitodensRegister.XXX.getAddress());
    }
}
