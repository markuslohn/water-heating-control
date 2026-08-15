package de.bimalo.homeauto.boundary.goecharger;

import de.bimalo.homeauto.boundary.modbus.AbstractModbusClient;
import de.bimalo.homeauto.entity.DeviceInfo;
import de.bimalo.homeauto.entity.Power;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GoEchargerModbusClient extends AbstractModbusClient {

    public GoEchargerModbusClient(String host, int port) {
        super(host, port);
    }

    @Override
    protected DeviceInfo readDeviceInfo() {
        return DeviceInfo.builder()
                .manufacturer("go-e")
                .model("go-eCharger")
                .serialNumber(readSerialNumber())
                .firmwareVersion(readFirmwareVersion())
                .build();
    }

    public CarStatus readCarStatus() {
        int statusCode = readInputUnsignedInteger(GoEchargerRegister.CAR_STATE.getAddress());
        return CarStatus.fromCode(statusCode);
    }

    private String readSerialNumber() {
        return readInputString(GoEchargerRegister.SNR.getAddress(), 6).trim();
    }

    private String readFirmwareVersion() {
        return readInputString(GoEchargerRegister.FWV.getAddress(), 2).trim();
    }

    public Power readPowerL1() {
        return readPower(GoEchargerRegister.POWER_L1.getAddress());
    }

    public Power readPowerL2() {
        return readPower(GoEchargerRegister.POWER_L2.getAddress());
    }

    public Power readPowerL3() {
        return readPower(GoEchargerRegister.POWER_L3.getAddress());
    }

    private Power readPower(int address) {
        long rawValue = readInputUnsigned32BitInteger(address);
        return Power.ofWatts(rawValue / 10);
    }

}
