package de.bimalo.homeauto.boundary.viessman;

import de.bimalo.homeauto.entity.GasHeatingStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Manual integration test for VitodensAdapter.
 * Communicates with the actual Vitodens gas heating system, no assertions -
 * intended for manual activation/deactivation of hot water production.
 */
@Tag("integration")
class VitodensAdapterIT {

    private VitodensAdapter service;

    @BeforeEach
    void setUp() {
        VitodensModbusClient client = new VitodensModbusClient("192.168.200.64", 502);
        service = new VitodensAdapter(null, client);
        service.initialize();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void activateGasHeating() throws InterruptedException {
        GasHeatingStatus statusBefore = service.readStatus();
        System.out.println("Current Temperature: " + statusBefore.currentTemperature());
        System.out.println("Target Temperature: " + statusBefore.targetTemperature());
        System.out.println("Heating active before: " + statusBefore.active());

        service.activateHeating();
        System.out.println("Heating active after: " + service.readStatus().active());

        // VitodensAdapter's @Scheduled keep-alive only runs inside a managed
        // Quarkus context, which this manual test deliberately doesn't bootstrap.
        // The Vitodens falls back to internal control if the external request
        // isn't refreshed within 25 seconds, so it is refreshed here explicitly
        // at that cadence instead (status is printed on the same cadence).
        for (int i = 1; i <= 12; i++) {
            Thread.sleep(25_000);
            service.keepExternalRequestAlive();
            GasHeatingStatus status = service.readStatus();
            System.out.println("[+" + (i * 25) + "s] Heating active: " + status.active()
                    + ", Current Temperature: " + status.currentTemperature());
        }
    }

    @Test
    void deactivateGasHeating() {
        System.out.println("Heating active before: " + service.readStatus().active());

        service.deactivateHeating();

        System.out.println("Heating active after: " + service.readStatus().active());
    }
}
