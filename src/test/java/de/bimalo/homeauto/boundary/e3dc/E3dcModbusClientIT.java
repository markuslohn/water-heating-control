package de.bimalo.homeauto.boundary.e3dc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Manual integration test for the E3/DC Modbus connection.
 * Communicates with the actual E3/DC battery storage, no assertions -
 * intended for manual verification that the device can be reached and read.
 */
@Tag("integration")
class E3dcModbusClientIT {

    private E3dcModbusClient client;

    @BeforeEach
    void setUp() {
        client = new E3dcModbusClient("192.168.200.48", 502);
        client.initialize();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void testAccess() throws Exception {
        client.testMagicByte();

        System.out.println("Production Power: " + client.readProductionPower());
        System.out.println("Battery Power: " + client.readBatteryPower());
        System.out.println("House Consumption Power: " + client.readHouseConsumptionPower());
        System.out.println("Grid Power: " + client.readGridPower());
        System.out.println("Battery State of Charge: " + client.readBatteryStateOfCharge());
    }
}
