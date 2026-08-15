package de.bimalo.homeauto.boundary.goecharger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Manual integration test for the go-eCharger Modbus connection.
 * Communicates with the actual go-eCharger wallbox, no assertions -
 * intended for manual verification that the device can be reached and read.
 */
@Tag("integration")
class GoEchargerModbusClientIT {

    private GoEchargerModbusClient client;

    @BeforeEach
    void setUp() {
        client = new GoEchargerModbusClient("192.168.200.13", 502);
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
        System.out.println("Car Status: " + client.readCarStatus());
        System.out.println("Power L1: " + client.readPowerL1());
        System.out.println("Power L2: " + client.readPowerL2());
        System.out.println("Power L3: " + client.readPowerL3());
    }
}
