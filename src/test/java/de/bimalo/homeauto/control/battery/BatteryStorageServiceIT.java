package de.bimalo.homeauto.control.battery;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Power;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integrationstest für den BatteryStorageService.
 * Dieser Test kommuniziert mit dem tatsächlichen E3/DC Batteriespeicher.
 *
 * Voraussetzungen:
 * - E3/DC System muss erreichbar sein
 * - Modbus TCP muss konfiguriert und aktiviert sein
 * - Korrekte IP-Adresse und Port müssen in application.properties konfiguriert
 * sein
 */
@Tag("integration")
class BatteryStorageServiceIT {

    private BatteryStorageService service;
    private BatteryStorageConfig config;

    @BeforeEach
    void setUp() {
        // Konfiguration für den tatsächlichen Batteriespeicher
        config = new BatteryStorageConfig() {
            @Override
            public ModbusConfig modbus() {
                return new ModbusConfig() {
                    @Override
                    public String host() {
                        return "192.168.200.48"; // IP-Adresse des E3/DC Systems
                    }

                    @Override
                    public int port() {
                        return 502; // Standard Modbus-Port
                    }
                };
            }
        };

        service = new BatteryStorageService(config);
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
        BatteryStatus status = service.getCurrentStatus();
        assertNotNull(status, "BatteryStatus should not be null");
        System.out.println(status);

        Power surplusPower = service.determineSolarPowerSurplus();
        assertNotNull(surplusPower, "Surplus Power should not be null");
        System.out.println("Surplus Power: " + surplusPower);
    }
}
