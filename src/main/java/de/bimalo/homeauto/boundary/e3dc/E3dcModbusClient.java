package de.bimalo.homeauto.boundary.e3dc;

import de.bimalo.homeauto.boundary.modbus.AbstractModbusClient;
import de.bimalo.homeauto.entity.DeviceInfo;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class E3dcModbusClient extends AbstractModbusClient {

    public E3dcModbusClient(String host, int port) {
        super(host, port);
    }

    @Override
    protected DeviceInfo readDeviceInfo() {
        return DeviceInfo.builder()
                .manufacturer(readString(E3dcRegister.MANUFACTURER.getAddress(), 16).trim())
                .model(readString(E3dcRegister.MODEL.getAddress(), 16).trim())
                .serialNumber(readString(E3dcRegister.SERIAL_NUMBER.getAddress(), 16).trim())
                .firmwareVersion(readString(E3dcRegister.FIRMWARE_VERSION.getAddress(), 16).trim())
                .build();
    }

    public void testMagicByte() {
        int rawValue = this.readUnsignedInteger(0);
        if (rawValue != 0xE3DC) {
            throw new IllegalStateException(
                    String.format("Invalid magic byte read from E3/DC device: 0x%04X (expected: 0xE3DC)", rawValue));
        }
    }

    public Power readProductionPower() {
        long rawValue = readUnsigned32BitInteger(E3dcRegister.PV_EXTENDED_POWER.getAddress());
        if (rawValue > 0) {
            rawValue = 4294967296L - rawValue;
        }
        return Power.ofWatts(rawValue);
    }

    public Power readBatteryPower() {
        return Power.ofWatts(read32BitInteger(E3dcRegister.BATTERY_POWER.getAddress()));
    }

    public Power readHouseConsumptionPower() {
        return Power.ofWatts(read32BitInteger(E3dcRegister.HOUSE_POWER.getAddress()));
    }

    public Power readGridPower() {
        return Power.ofWatts(read32BitInteger(E3dcRegister.GRID_POWER.getAddress()));
    }

    public Percentage readBatteryStateOfCharge() {
        return Percentage.of(readUnsignedInteger(E3dcRegister.BATTERY_SOC.getAddress()));
    }

    /**
     * E3/DC uses little-endian word order (low word first) and signed
     * interpretation.
     * Overrides the default big-endian word order composition.
     *
     * @param word0 first word (bytes 0-1) - contains low word for E3/DC
     * @param word1 second word (bytes 2-3) - contains high word for E3/DC
     * @return the composed signed 32-bit value
     */
    @Override
    protected long compose32BitValue(int word0, int word1) {
        // E3/DC uses little-endian word order: word0 = low word, word1 = high word
        int lowWord = word0;
        int highWord = word1;

        // Compose unsigned value
        long uvalue = ((long) highWord << 16) | lowWord;

        // Convert to signed if necessary (check MSB of high word)
        if (highWord < 32768) {
            return uvalue; // positive value
        } else {
            return uvalue - 4294967296L; // negative: subtract 2^32 to get signed value
        }
    }

    /**
     * E3/DC uses little-endian word order (low word first) for unsigned values.
     * Overrides the default big-endian word order composition.
     *
     * @param word0 first word (bytes 0-1) - contains low word for E3/DC
     * @param word1 second word (bytes 2-3) - contains high word for E3/DC
     * @return the composed unsigned 32-bit value
     */
    @Override
    protected long composeUnsigned32BitValue(int word0, int word1) {
        // E3/DC uses little-endian word order: word0 = low word, word1 = high word
        int lowWord = word0;
        int highWord = word1;

        // Compose unsigned value (no sign conversion needed)
        return ((long) highWord << 16) | lowWord;
    }

}
