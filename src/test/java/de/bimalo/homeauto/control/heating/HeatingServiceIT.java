package de.bimalo.homeauto.control.heating;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.bimalo.homeauto.entity.Temperature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public class HeatingServiceIT {

    private HeatingConfig config;
    private HeatingService service;

    @BeforeEach
    void setUp() {
        config = new HeatingConfig() {
            @Override
            public ModbusConfig modbus() {
                return new ModbusConfig() {
                    @Override
                    public String host() {
                        return "192.168.200.31";
                    }

                    @Override
                    public int port() {
                        return 502;
                    }
                };
            }
        };

        service = new HeatingService(config);
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
        Temperature hotWaterTemp = service.readHotWaterTemperature();
        assertNotNull(hotWaterTemp);

        System.out.println("Hotwater Temperature: " + hotWaterTemp);
        Temperature outsideTemp = service.readOutsideTemperature();
        assertNotNull(outsideTemp);
        System.out.println("Outside Temperature: " + outsideTemp);

        int hotWaterStatus = service.readHotWaterStatus();
        System.out.println("Hotwater Status: " + hotWaterStatus);
    }

}
