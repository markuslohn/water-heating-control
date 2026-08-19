package de.bimalo.homeauto.boundary.elwa2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.modbus.ModbusClientException;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import java.time.Duration;
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
        // Short enough to satisfy validatePowerCommandTimeout() for any timeout used below
        lenient().when(config.keepAliveCheckInterval()).thenReturn(Duration.ofMillis(1));
    }

    @Test
    void readMeasurements_returnsMeasurementsFromModbusClient() {
        when(modbusClient.readTemperature1()).thenReturn(Temperature.ofCelsius(45.2));
        when(modbusClient.readTargetTemperature()).thenReturn(Temperature.ofCelsius(60.0));
        when(modbusClient.readPower()).thenReturn(Power.ofWatts(1500));

        Instant before = Instant.now();
        HeatingRodStatus measurements = service.readMeasurements();
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

        HeatingRodStatus measurements = service.readMeasurements();

        assertEquals(Optional.of(measurements), service.getLastKnownMeasurements());
    }

    @Test
    void readMeasurements_propagatesException_whenModbusCommunicationFails() {
        when(modbusClient.readTemperature1()).thenThrow(new RuntimeException("Modbus timeout"));

        assertThrows(RuntimeException.class, () -> service.readMeasurements());
    }

    @Test
    void getLastKnownMeasurements_returnsEmpty_whenNoMeasurementsReadYet() {
        assertEquals(Optional.empty(), service.getLastKnownMeasurements());
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
        when(modbusClient.readPowerCommandTimeout()).thenReturn(Duration.ofSeconds(60));
        service.initialize();

        service.adjustHeating(Power.ofWatts(1000));
        service.keepHeatingAlive();

        verify(modbusClient, times(1)).setPower(Power.ofWatts(1000));
    }

    @Test
    void keepHeatingAlive_refreshes_whenElapsedTimeReachesHalfTimeout() throws InterruptedException {
        when(modbusClient.readPowerCommandTimeout()).thenReturn(Duration.ofMillis(4));
        service.initialize();

        service.adjustHeating(Power.ofWatts(1000));
        Thread.sleep(20);
        service.keepHeatingAlive();

        verify(modbusClient, times(2)).setPower(Power.ofWatts(1000));
    }

    @Test
    void initialize_usesFallbackTimeout_whenReadingPowerCommandTimeoutFails() throws InterruptedException {
        // readPowerCommandTimeout() is now only read once during initialize(); a failure
        // there falls back to the configured default instead of failing startup
        when(modbusClient.readPowerCommandTimeout())
                .thenThrow(new ModbusClientException("Modbus timeout", "localhost", 502));
        when(config.powerCommandTimeoutFallback()).thenReturn(Duration.ofMillis(4));

        assertDoesNotThrow(() -> service.initialize());

        // The fallback value must actually be used by keepHeatingAlive(), not just avoid
        // throwing during initialize()
        service.adjustHeating(Power.ofWatts(1000));
        Thread.sleep(20);
        service.keepHeatingAlive();

        verify(modbusClient, times(2)).setPower(Power.ofWatts(1000));
    }
}
