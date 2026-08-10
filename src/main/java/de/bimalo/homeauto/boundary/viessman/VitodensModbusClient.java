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

    private int convertToRawValue(int value, double factor) {
        return Double.valueOf(value * factor).intValue();
    }

    private int convertToRawValue(double value, double factor) {
        return Double.valueOf(value * factor).intValue();
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
        this.writeUnsignedInteger(register.getAddress(),
                convertToRawValue(requestMode.getValue(), register.getWriteFactor()));
    }

    public ExternalRequestMode readExternalRequestStatus() {
        VitodensRegister register = VitodensRegister.EXTERNAL_REQUEST_STATUS;
        int rawValue = convertToRawValue(this.readInputUnsignedInteger(register.getAddress()),
                register.getReadFactor());
        return ExternalRequestMode.fromValue(rawValue);
    }

    public Temperature readHotWaterTargetTemperature() {
        VitodensRegister register = VitodensRegister.HOT_WATER_TARGET_TEMPERATUR;
        double rawValue = convertToRawValue(this.readInteger(register.getAddress()), register.getReadFactor());
        return Temperature.ofCelsius(rawValue);
    }

    public void writeHotWaterTargetTemperature(Temperature temperature) {
        VitodensRegister register = VitodensRegister.HOT_WATER_TARGET_TEMPERATUR;
        this.writeUnsignedInteger(register.getAddress(),
                convertToRawValue(temperature.getCelsius(), register.getWriteFactor()));
    }

    public void writeHotWaterHeatingProgram(HotWaterProgram program) {
        if (program.equals(HotWaterProgram.UNKNOWN)) {
            log.warn("Cannot write unknown hot water program to device.");
            return;
        }
        VitodensRegister register = VitodensRegister.HOT_WATER_HEATING_PROGRAMM_TARGET;
        log.trace("Write value {} to register {}, {}.", program.getValue(), register.getAddress(),
                register.getDescription());
        this.writeUnsignedInteger(register.getAddress(),
                convertToRawValue(program.getValue(), register.getWriteFactor()));
    }

    public HotWaterProgram readHotWaterHeatingProgramCurrentStatus() {
        VitodensRegister register = VitodensRegister.HOT_WATER_HEATING_PROGRAMM_CURRENT;
        int rawValue = convertToRawValue(this.readInteger(register.getAddress()), register.getReadFactor());
        return HotWaterProgram.fromValue(rawValue);
    }

    public Temperature readHotWaterCurrentTemperature() {
        VitodensRegister register = VitodensRegister.HOT_WATER_CURRENT_TEMPERATURE;
        double rawValue = convertToRawValue(this.readInputInteger(register.getAddress()), register.getReadFactor());
        return Temperature.ofCelsius(rawValue);
    }

    public Temperature readOutsideTemperature() {
        VitodensRegister register = VitodensRegister.OUTSIDE_TEMPERATURE;
        double rawValue = convertToRawValue(this.readInputInteger(register.getAddress()), register.getReadFactor());
        return Temperature.ofCelsius(rawValue);
    }

    public HotWaterStatus readHotWaterStatus() {
        VitodensRegister register = VitodensRegister.HOT_WATER_STATUS;
        int rawValue = convertToRawValue(this.readInputUnsignedInteger(register.getAddress()),
                register.getReadFactor());
        return HotWaterStatus.fromValue(rawValue);
    }

    public Volume readHotWaterGasConsumptionToday() {
        VitodensRegister register = VitodensRegister.HOT_WATER_GAS_CONSUMPTION_TODAY;
        double rawValue = convertToRawValue(this.readInputInteger(register.getAddress()), register.getReadFactor());
        return Volume.ofCubicMeters(rawValue);
    }

    public Volume readHotWaterGasConsumptionThisMonth() {
        VitodensRegister register = VitodensRegister.HOT_WATER_GAS_CONSUMPTION_THIS_MONTH;
        double rawValue = convertToRawValue(this.readInputInteger(register.getAddress()), register.getReadFactor());
        return Volume.ofCubicMeters(rawValue);
    }
}
