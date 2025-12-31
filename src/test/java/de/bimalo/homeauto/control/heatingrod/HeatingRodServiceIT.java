package de.bimalo.homeauto.control.heatingrod;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integrationstest für den HeatingRodService.
 * Dieser Test kommuniziert mit dem tatsächlichen ELWA2 Heizstab.
 *
 * Voraussetzungen:
 * - ELWA2 System muss erreichbar sein
 * - Modbus TCP muss konfiguriert und aktiviert sein
 * - Korrekte IP-Adresse und Port müssen in application.properties konfiguriert
 * sein
 */
@Tag("integration")
class HeatingRodServiceIT {

    private HeatingRodService service;
    private HeatingRodConfig config;

    @BeforeEach
    void setUp() {
        // Konfiguration für den tatsächlichen Heizstab
        config = new HeatingRodConfig() {
            @Override
            public ModbusConfig modbus() {
                return new ModbusConfig() {
                    @Override
                    public String host() {
                        return "192.168.200.73"; // IP-Adress of ELWA2
                    }

                    @Override
                    public int port() {
                        return 502; // Standard Modbus-Port
                    }
                };
            }
        };

        service = new HeatingRodService(config);
        service.initialize();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void testAccess() throws Exception {
        Temperature temperature1 = service.readTemperature1();
        assertNotNull(temperature1);
        System.out.println("Temperature 1: " + temperature1);

        System.out.println("Status: " + service.readDeviceStatus());
        System.out.println("Power: " + service.readPower());
        System.out.println("Max Power: " + service.readMaxPower());
        System.out.println("Power Timeout: " + service.readPowerTimeout() + " seconds");
        System.out.println("Target Temperature: " + service.readTargetTemperature());
        Power targetPower = Power.ofWatts(1300);
        // service.setPower(targetPower);

        System.out.println("Status: " + service.readDeviceStatus());
        System.out.println("Power: " + service.readPower());
        System.out.println("Max Power: " + service.readMaxPower());

    }
}
