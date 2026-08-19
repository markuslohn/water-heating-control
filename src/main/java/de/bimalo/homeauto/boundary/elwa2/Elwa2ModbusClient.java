package de.bimalo.homeauto.boundary.elwa2;

import de.bimalo.homeauto.boundary.modbus.AbstractModbusClient;
import de.bimalo.homeauto.boundary.modbus.ModbusClientException;
import de.bimalo.homeauto.entity.DeviceInfo;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Elwa2ModbusClient extends AbstractModbusClient {

    /**
     * Maximum power supported by the ELWA2 heating rod in watts.
     */
    public static final int MAX_POWER_WATTS = 3000;
    private static final Power MAX_POWER = Power.ofWatts(MAX_POWER_WATTS);

    public Elwa2ModbusClient(String host, int port) {
        super(host, port);
    }

    @Override
    protected DeviceInfo readDeviceInfo() {
        return DeviceInfo.builder()
                .manufacturer("test")
                .model("ELWA2")
                .serialNumber(readSerialNumber())
                .firmwareVersion(readFirmwareVersion())
                .build();
    }

    private String readSerialNumber() {
        StringBuilder serialNumber = new StringBuilder();
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_2.getAddress(), 1).trim());
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_4.getAddress(), 1).trim());
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_6.getAddress(), 1).trim());
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_8.getAddress(), 1).trim());
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_10.getAddress(), 1).trim());
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_12.getAddress(), 1).trim());
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_14.getAddress(), 1).trim());
        serialNumber.append(readString(Elwa2Register.SERIAL_NUMBER_16.getAddress(), 1).trim());
        return serialNumber.toString();
    }

    private String readFirmwareVersion() {
        return String.valueOf(this.readUnsignedInteger(Elwa2Register.FIRMWARE_VERSION.getAddress()));
    }

    public Temperature readTemperature1() {
        Elwa2Register register = Elwa2Register.TEMP_1;
        int rawValue = this.readUnsignedInteger(register.getAddress());
        return Temperature.ofCelsius(rawValue * register.getScaleFactor());
    }

    public Temperature readTargetTemperature() {
        Elwa2Register register = Elwa2Register.TARGET_TEMP;
        int rawValue = this.readUnsignedInteger(register.getAddress());
        return Temperature.ofCelsius(rawValue * register.getScaleFactor());
    }

    public Elwa2Status readStatus() {
        int rawValue = this.readUnsignedInteger(Elwa2Register.STATUS.getAddress());
        return Elwa2Status.fromValue(rawValue);
    }

    public Power readPower() {
        return Power.ofWatts(this.readUnsignedInteger(Elwa2Register.POWER.getAddress()));
    }

    public Power readMaxPower() {
        return Power.ofWatts(this.readUnsignedInteger(Elwa2Register.MAX_POWER.getAddress()));
    }

    public Duration readPowerCommandTimeout() {
        return Duration.ofSeconds(this.readUnsignedInteger(Elwa2Register.POWER_TIMEOUT.getAddress()));
    }

    /**
     * Sets the power of the ELWA2 heating rod.
     *
     * @param power the desired power
     * @throws NullPointerException     if power is null
     * @throws IllegalArgumentException if power is negative or exceeds
     *                                  MAX_POWER_WATTS
     */
    public void setPower(Power power) {
        Objects.requireNonNull(power, "Power must not be null");
        if (power.isNegative() || power.isGreaterThan(MAX_POWER)) {
            throw new IllegalArgumentException(
                    String.format("Power must be between 0 and %d W, but was: %s", MAX_POWER_WATTS, power));
        }
        this.writeUnsignedInteger(Elwa2Register.POWER.getAddress(), (int) power.getWatts());
    }
}
