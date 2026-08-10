package de.bimalo.homeauto.boundary.viessman;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Manual integration test for the Vitodens Modbus connection.
 * Communicates with the actual Viessmann heating system, no assertions -
 * intended for manual verification that the device can be reached and read.
 */
@Tag("integration")
class VitodensModbusClientIT {

    private VitodensModbusClient client;

    @BeforeEach
    void setUp() {
        client = new VitodensModbusClient("192.168.200.64", 502);
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
        System.out.println("Gateway connected: " + client.gatewayConnected());
        System.out.println("External request status: " + client.readExternalRequestStatus());

        System.out.println("Hotwater Temperature: " + client.readHotWaterCurrentTemperature());
        System.out.println("Hotwater Target Temperature: " + client.readHotWaterTargetTemperature());

        System.out.println("Outside Temperature: " + client.readOutsideTemperature());

        System.out.println("Hotwater Status: " + client.readHotWaterStatus());
        System.out.println("Hotwater Program: " + client.readHotWaterHeatingProgramCurrentStatus());

        System.out.println("GAS consumption today: " + client.readHotWaterGasConsumptionToday());
        System.out.println("GAS consumption this month: " + client.readHotWaterGasConsumptionThisMonth());
    }
}
