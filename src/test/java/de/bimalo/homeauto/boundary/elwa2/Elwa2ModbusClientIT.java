package de.bimalo.homeauto.boundary.elwa2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Manual integration test for the ELWA2 Modbus connection.
 * Communicates with the actual ELWA2 heating rod, no assertions -
 * intended for manual verification that the device can be reached and read.
 */
@Tag("integration")
class Elwa2ModbusClientIT {

    private Elwa2ModbusClient client;

    @BeforeEach
    void setUp() {
        client = new Elwa2ModbusClient("192.168.200.73", 502);
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
        System.out.println("Temperature 1: " + client.readTemperature1());
        System.out.println("Target Temperature: " + client.readTargetTemperature());

        System.out.println("Status: " + client.readStatus());
        System.out.println("Power: " + client.readPower());
        System.out.println("Max Power: " + client.readMaxPower());
        System.out.println("Power Timeout: " + client.readPowerTimeout());

        // client.setPower(Power.ofWatts(1300));

        System.out.println("Status: " + client.readStatus());
        System.out.println("Power: " + client.readPower());
        System.out.println("Max Power: " + client.readMaxPower());
    }
}
