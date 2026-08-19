package de.bimalo.homeauto.boundary.elwa2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for Elwa2Adapter.
 * Mocks Modbus device communication to test the control logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class Elwa2AdapterTest {

    @Mock
    private Elwa2Config config;

    @Mock
    private Elwa2ModbusClient modbusClient;

    private Elwa2Adapter service;

    @BeforeEach
    void setUp() {
        service = new Elwa2Adapter(config, modbusClient);
    }

    @Test
    void readTemperature1_returnsTemperatureFromModbusClient() {
        when(modbusClient.readTemperature1()).thenReturn(Temperature.ofCelsius(45.2));

        assertEquals(Temperature.ofCelsius(45.2), service.readTemperature1());
    }

    @Test
    void readTargetTemperature_returnsTemperatureFromModbusClient() {
        when(modbusClient.readTargetTemperature()).thenReturn(Temperature.ofCelsius(60.0));

        assertEquals(Temperature.ofCelsius(60.0), service.readTargetTemperature());
    }

    @Test
    void readPower_returnsPowerFromModbusClient() {
        when(modbusClient.readPower()).thenReturn(Power.ofWatts(1500));

        assertEquals(Power.ofWatts(1500), service.readPower());
    }

    @Test
    void readMaxPower_returnsPowerFromModbusClient() {
        when(modbusClient.readMaxPower()).thenReturn(Power.ofWatts(3200));

        assertEquals(Power.ofWatts(3200), service.readMaxPower());
    }

    @Test
    void readPowerTimeout_returnsDurationFromModbusClient() {
        when(modbusClient.readPowerTimeout()).thenReturn(Duration.ofSeconds(30));

        assertEquals(Duration.ofSeconds(30), service.readPowerTimeout());
    }

    @Test
    void adjustHeating_writesPowerToModbusClient() {
        service.adjustHeating(Power.ofWatts(1200));

        verify(modbusClient).setPower(Power.ofWatts(1200));
    }

    @Test
    void adjustHeating_throwsNullPointerException_whenPowerIsNull() {
        assertThrows(NullPointerException.class, () -> service.adjustHeating(null));
    }

    @Test
    void keepHeatingAlive_doesNothingWhenNoPowerRequested() {
        service.keepHeatingAlive();

        verify(modbusClient, never()).setPower(org.mockito.ArgumentMatchers.any(Power.class));
    }

    @Test
    void keepHeatingAlive_doesNotRefresh_whenElapsedTimeBelowHalfTimeout() {
        when(modbusClient.readPowerTimeout()).thenReturn(Duration.ofSeconds(60));

        service.adjustHeating(Power.ofWatts(1000));
        service.keepHeatingAlive();

        verify(modbusClient, times(1)).setPower(Power.ofWatts(1000));
    }

    @Test
    void keepHeatingAlive_refreshes_whenElapsedTimeReachesHalfTimeout() throws InterruptedException {
        when(modbusClient.readPowerTimeout()).thenReturn(Duration.ofMillis(4));

        service.adjustHeating(Power.ofWatts(1000));
        Thread.sleep(20);
        service.keepHeatingAlive();

        verify(modbusClient, times(2)).setPower(Power.ofWatts(1000));
    }

    @Test
    void keepHeatingAlive_swallowsException_whenModbusCommunicationFails() {
        when(modbusClient.readPowerTimeout()).thenThrow(new RuntimeException("Modbus timeout"));

        service.adjustHeating(Power.ofWatts(1000));

        assertDoesNotThrow(() -> service.keepHeatingAlive());
    }
}
