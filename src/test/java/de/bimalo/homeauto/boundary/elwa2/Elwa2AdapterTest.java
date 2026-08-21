package de.bimalo.homeauto.boundary.elwa2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Instant;
import java.util.Optional;
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
    void readMeasurements_returnsMeasurementsFromModbusClient() {
        when(modbusClient.readTemperature1()).thenReturn(Temperature.ofCelsius(45.2));
        when(modbusClient.readTargetTemperature()).thenReturn(Temperature.ofCelsius(60.0));
        when(modbusClient.readPower()).thenReturn(Power.ofWatts(1500));

        Instant before = Instant.now();
        HeatingRodStatus measurements = service.readStatus();
        Instant after = Instant.now();

        assertEquals(Temperature.ofCelsius(45.2), measurements.currentTemperature());
        assertEquals(Temperature.ofCelsius(60.0), measurements.targetTemperature());
        assertEquals(Power.ofWatts(1500), measurements.currentPower());
        assertFalse(measurements.measuredAt().isBefore(before));
        assertFalse(measurements.measuredAt().isAfter(after));
    }

    @Test
    void readMeasurements_updatesLastKnownMeasurements() {
        when(modbusClient.readTemperature1()).thenReturn(Temperature.ofCelsius(45.2));
        when(modbusClient.readTargetTemperature()).thenReturn(Temperature.ofCelsius(60.0));
        when(modbusClient.readPower()).thenReturn(Power.ofWatts(1500));

        HeatingRodStatus measurements = service.readStatus();

        assertEquals(Optional.of(measurements), service.getLastKnownStatus());
    }

    @Test
    void readMeasurements_propagatesException_whenModbusCommunicationFails() {
        when(modbusClient.readTemperature1()).thenThrow(new RuntimeException("Modbus timeout"));

        assertThrows(RuntimeException.class, () -> service.readStatus());
    }

    @Test
    void getLastKnownMeasurements_returnsEmpty_whenNoMeasurementsReadYet() {
        assertEquals(Optional.empty(), service.getLastKnownStatus());
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
    void stopHeating_writesZeroOnlyOnce_whenCalledRepeatedlyWhileAlreadyStopped() {
        service.stopHeating();
        service.stopHeating();

        verify(modbusClient, times(1)).setPower(Power.ZERO);
    }

    @Test
    void adjustHeating_writesZeroOnlyOnce_whenRepeatedlyToldToStop() {
        service.adjustHeating(Power.ofWatts(500));
        service.adjustHeating(Power.ZERO);
        service.adjustHeating(Power.ZERO);

        verify(modbusClient, times(1)).setPower(Power.ZERO);
    }

    @Test
    void stopHeating_stillWritesOnFirstCall_evenThoughLastRequestedPowerDefaultsToZero() {
        // Safety net: a freshly constructed instance must not assume the real
        // device is already off just because its own tracked state defaults to
        // zero (e.g. right after an application restart while heating was on).
        service.stopHeating();

        verify(modbusClient, times(1)).setPower(Power.ZERO);
    }

    @Test
    void stopHeating_retriesOnNextCall_whenPreviousStopAttemptFailed() {
        service.adjustHeating(Power.ofWatts(500));
        doThrow(new RuntimeException("write failed")).doNothing().when(modbusClient).setPower(Power.ZERO);

        assertThrows(RuntimeException.class, () -> service.stopHeating());
        // The failed write must not be treated as "already stopped" - the device
        // is presumably still heating at 500W, so a second attempt must reach the
        // Modbus client again instead of being skipped by the idempotency guard.
        service.stopHeating();

        verify(modbusClient, times(2)).setPower(Power.ZERO);
    }

    @Test
    void shutdown_stopsHeatingRodAndShutsDownModbusClient() {
        service.shutdown();

        verify(modbusClient).setPower(Power.ZERO);
        verify(modbusClient).shutdown();
    }

    @Test
    void shutdown_stillShutsDownModbusClient_whenStoppingHeatingFails() {
        service.adjustHeating(Power.ofWatts(500));
        doThrow(new RuntimeException("stop failed")).when(modbusClient).setPower(Power.ZERO);

        assertDoesNotThrow(() -> service.shutdown());

        verify(modbusClient).shutdown();
    }
}
