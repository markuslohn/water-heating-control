package de.bimalo.homeauto.control.battery;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bimalo.homeauto.entity.PowerStatus;

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
 * - Korrekte IP-Adresse und Port müssen in application.properties konfiguriert sein
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
                        return "192.168.200.48";  // IP-Adresse des E3/DC Systems
                    }
                    
                    @Override
                    public int port() {
                        return 502;  // Standard Modbus-Port
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
    void testGetCurrentPowerStatus() throws Exception {
        // When
        PowerStatus status = service.getCurrentPowerStatus();
        
        // Then
        assertNotNull(status, "PowerStatus sollte nicht null sein");
        assertNotNull(status.getTimestamp(), "Zeitstempel sollte gesetzt sein");
        
        // PV-Leistung sollte im sinnvollen Bereich sein (0 - 15kW)
        assertTrue(status.getPowerProduction() >= 0, 
            "PV-Leistung sollte nicht negativ sein");
        assertTrue(status.getPowerProduction() <= 15000, 
            "PV-Leistung sollte nicht über 15kW sein");
        
        // Hausverbrauch sollte im sinnvollen Bereich sein (0 - 20kW)
        assertTrue(status.getPowerConsumption() >= 0, 
            "Hausverbrauch sollte nicht negativ sein");
        assertTrue(status.getPowerConsumption() <= 20000, 
            "Hausverbrauch sollte nicht über 20kW sein");
        
        // Batterieladung sollte zwischen 0 und 100% sein
        assertTrue(status.getBatteryStateOfCharge() >= 0, 
            "Batterieladung sollte nicht unter 0% sein");
        assertTrue(status.getBatteryStateOfCharge() <= 100, 
            "Batterieladung sollte nicht über 100% sein");
        
        // Log für manuelle Überprüfung
        System.out.println("PowerStatus: " + status);
    }
}