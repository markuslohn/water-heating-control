package de.bimalo.homeauto.boundary.viessman;

import de.bimalo.homeauto.boundary.modbus.AbstractModbusClient;
import de.bimalo.homeauto.entity.DeviceInfo;
import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.Volume;
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
                .serialNumber("7372085")
                .firmwareVersion("04.02.13(24)")
                .build();
    }

    public boolean gatewayConnected() {
        VitodensRegister register = VitodensRegister.STATUS;
        int rawValue = readDiscreteInputStatus(register.getAddress());
        return rawValue == 1;
    }

    public void writeExternalRequest(ExternalRequestMode requestMode) {
        if (requestMode.equals(ExternalRequestMode.UNKNOWN)) {
            log.warn("Cannot write unknown external request mode to device.");
            return;
        }
        VitodensRegister register = VitodensRegister.EXTERNAL_REQUEST;
        log.trace("Write value {} to register {}, {}.", requestMode.getValue(), register.getAddress(),
                register.getDescription());
        writeUnsignedInteger(register.getAddress(),
                scaleWriteValue(requestMode.getValue(), register.getWriteFactor()));
    }

    public ExternalRequestMode readExternalRequestStatus() {
        VitodensRegister register = VitodensRegister.EXTERNAL_REQUEST_STATUS;
        int rawValue = this.readInputUnsignedInteger(register.getAddress());
        return ExternalRequestMode.fromValue(scaleReadEnumValue(rawValue, register.getReadFactor()));
    }

    public Temperature readHotWaterTargetTemperature() {
        VitodensRegister register = VitodensRegister.HOT_WATER_TARGET_TEMPERATUR;
        int rawValue = readInteger(register.getAddress());
        double temperature = scaleReadValue(rawValue, register.getReadFactor());
        return Temperature.ofCelsius(temperature);
    }

    public void writeHotWaterTargetTemperature(Temperature temperature) {
        VitodensRegister register = VitodensRegister.HOT_WATER_TARGET_TEMPERATUR;
        writeUnsignedInteger(register.getAddress(),
                scaleWriteValue(temperature.celsius(), register.getWriteFactor()));
    }

    public void writeHotWaterHeatingProgram(HotWaterProgram program) {
        if (program.equals(HotWaterProgram.UNKNOWN)) {
            log.warn("Cannot write unknown hot water program to device.");
            return;
        }
        VitodensRegister register = VitodensRegister.HOT_WATER_HEATING_PROGRAMM_TARGET;
        log.trace("Write value {} to register {}, {}.", program.getValue(), register.getAddress(),
                register.getDescription());
        writeUnsignedInteger(register.getAddress(), scaleWriteValue(program.getValue(), register.getWriteFactor()));
    }

    public HotWaterProgram readHotWaterHeatingProgramCurrentStatus() {
        VitodensRegister register = VitodensRegister.HOT_WATER_HEATING_PROGRAMM_CURRENT;
        int rawValue = this.readInteger(register.getAddress());
        return HotWaterProgram.fromValue(scaleReadEnumValue(rawValue, register.getReadFactor()));
    }

    public Temperature readHotWaterCurrentTemperature() {
        VitodensRegister register = VitodensRegister.HOT_WATER_CURRENT_TEMPERATURE;
        int rawValue = readInputInteger(register.getAddress());
        double temperature = scaleReadValue(rawValue, register.getReadFactor());
        return Temperature.ofCelsius(temperature);
    }

    public Temperature readOutsideTemperature() {
        VitodensRegister register = VitodensRegister.OUTSIDE_TEMPERATURE;
        int rawValue = readInputInteger(register.getAddress());
        double temperature = scaleReadValue(rawValue, register.getReadFactor());
        return Temperature.ofCelsius(temperature);
    }

    public HotWaterStatus readHotWaterStatus() {
        VitodensRegister register = VitodensRegister.HOT_WATER_STATUS;
        int rawValue = this.readInputUnsignedInteger(register.getAddress());
        return HotWaterStatus.fromValue(scaleReadEnumValue(rawValue, register.getReadFactor()));
    }

    public Volume readHotWaterGasConsumptionToday() {
        VitodensRegister register = VitodensRegister.HOT_WATER_GAS_CONSUMPTION_TODAY;
        int rawValue = readInputInteger(register.getAddress());
        double cubicMeters = scaleReadValue(rawValue, register.getReadFactor());
        return Volume.ofCubicMeters(cubicMeters);
    }

    public Volume readHotWaterGasConsumptionThisMonth() {
        VitodensRegister register = VitodensRegister.HOT_WATER_GAS_CONSUMPTION_THIS_MONTH;
        int rawValue = readInputInteger(register.getAddress());
        double cubicMeters = scaleReadValue(rawValue, register.getReadFactor());
        return Volume.ofCubicMeters(cubicMeters);
    }
}
