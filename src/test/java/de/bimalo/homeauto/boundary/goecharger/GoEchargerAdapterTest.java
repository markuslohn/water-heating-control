package de.bimalo.homeauto.boundary.goecharger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.entity.CarStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.WallboxStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for GoEchargerAdapter.
 * Mocks Modbus device communication to test the adapter logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class GoEchargerAdapterTest {

    @Mock
    private GoEchargerConfig config;

    @Mock
    private GoEchargerModbusClient modbusClient;

    private GoEchargerAdapter service;

    @BeforeEach
    void setUp() {
        service = new GoEchargerAdapter(config, modbusClient);
    }

    @Test
    void readStatus_returnsStatusFromModbusClient() {
        when(modbusClient.readCarStatus()).thenReturn(CarStatus.CHARGING);
        when(modbusClient.readPowerL1()).thenReturn(Power.ofWatts(1000));
        when(modbusClient.readPowerL2()).thenReturn(Power.ofWatts(1100));
        when(modbusClient.readPowerL3()).thenReturn(Power.ofWatts(900));

        WallboxStatus status = service.readStatus();

        assertEquals(CarStatus.CHARGING, status.operatingStatus());
        assertEquals(Power.ofWatts(3000), status.chargingPower());
    }

    @Test
    void readStatus_updatesLastKnownStatus() {
        when(modbusClient.readCarStatus()).thenReturn(CarStatus.READY);
        when(modbusClient.readPowerL1()).thenReturn(Power.ZERO);
        when(modbusClient.readPowerL2()).thenReturn(Power.ZERO);
        when(modbusClient.readPowerL3()).thenReturn(Power.ZERO);

        WallboxStatus status = service.readStatus();

        assertEquals(Optional.of(status), service.getLastKnownStatus());
    }

    @Test
    void readStatus_propagatesException_whenModbusCommunicationFails() {
        when(modbusClient.readCarStatus()).thenThrow(new RuntimeException("Modbus timeout"));

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
