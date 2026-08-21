package de.bimalo.homeauto.boundary.e3dc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.entity.BatteryStatus;
import de.bimalo.homeauto.entity.Percentage;
import de.bimalo.homeauto.entity.Power;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for E3dcAdapter.
 * Mocks Modbus device communication to test the adapter logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class E3dcAdapterTest {

    @Mock
    private E3dcConfig config;

    @Mock
    private E3dcModbusClient modbusClient;

    private E3dcAdapter service;

    @BeforeEach
    void setUp() {
        service = new E3dcAdapter(config, modbusClient);
    }

    @Test
    void readStatus_returnsStatusFromModbusClient() {
        when(modbusClient.readProductionPower()).thenReturn(Power.ofWatts(2000));
        when(modbusClient.readHouseConsumptionPower()).thenReturn(Power.ofWatts(500));
        when(modbusClient.readBatteryPower()).thenReturn(Power.ofWatts(300));
        when(modbusClient.readGridPower()).thenReturn(Power.ZERO);
        when(modbusClient.readBatteryStateOfCharge()).thenReturn(Percentage.of(70));

        BatteryStatus status = service.readStatus();

        assertEquals(Power.ofWatts(2000), status.productionPower());
        assertEquals(Power.ofWatts(500), status.consumptionPower());
        assertEquals(Power.ofWatts(300), status.batteryPower());
        assertEquals(Power.ZERO, status.gridPower());
        assertEquals(Percentage.of(70), status.batteryStateOfCharge());
    }

    @Test
    void readStatus_updatesLastKnownStatus() {
        when(modbusClient.readProductionPower()).thenReturn(Power.ZERO);
        when(modbusClient.readHouseConsumptionPower()).thenReturn(Power.ZERO);
        when(modbusClient.readBatteryPower()).thenReturn(Power.ZERO);
        when(modbusClient.readGridPower()).thenReturn(Power.ZERO);
        when(modbusClient.readBatteryStateOfCharge()).thenReturn(Percentage.of(50));

        BatteryStatus status = service.readStatus();

        assertEquals(Optional.of(status), service.getLastKnownStatus());
    }

    @Test
    void readStatus_propagatesException_whenModbusCommunicationFails() {
        when(modbusClient.readProductionPower()).thenThrow(new RuntimeException("Modbus timeout"));

        assertThrows(RuntimeException.class, () -> service.readStatus());
    }

    @Test
    void getLastKnownStatus_returnsEmpty_whenNoStatusReadYet() {
        assertEquals(Optional.empty(), service.getLastKnownStatus());
    }

    @Test
    void isConnected_delegatesToModbusClient() {
        when(modbusClient.isConnected()).thenReturn(true);

        assertTrue(service.isConnected());
    }

    @Test
    void isConnected_returnsFalse_whenModbusClientNotConnected() {
        when(modbusClient.isConnected()).thenReturn(false);

        assertFalse(service.isConnected());
    }

    @Test
    void shutdown_delegatesToModbusClient() {
        service.shutdown();

        verify(modbusClient).shutdown();
    }
}
