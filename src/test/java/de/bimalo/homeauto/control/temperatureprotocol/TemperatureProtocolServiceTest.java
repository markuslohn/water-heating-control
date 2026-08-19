package de.bimalo.homeauto.control.temperatureprotocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.boundary.elwa2.Elwa2OperatingStatus;
import de.bimalo.homeauto.boundary.temperatureprotocol.TemperatureProtocolFileWriter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.TemperatureLogEntry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemperatureProtocolServiceTest {

    @Mock
    private TemperatureProtocolConfig config;

    @Mock
    private Elwa2Adapter elwa2Adapter;

    @Mock
    private VitodensAdapter vitodensAdapter;

    @Mock
    private TemperatureProtocolFileWriter fileWriter;

    @InjectMocks
    private TemperatureProtocolService service;

    @Test
    void recordTemperatures_doesNothing_whenDisabled() {
        when(config.enabled()).thenReturn(false);

        service.recordTemperatures();

        verify(elwa2Adapter, never()).readMeasurements();
        verify(fileWriter, never()).append(any());
    }

    @Test
    void recordTemperatures_writesEntryWithBothTemperatures_whenEnabled() {
        when(config.enabled()).thenReturn(true);
        when(elwa2Adapter.readMeasurements()).thenReturn(
                new HeatingRodStatus(Temperature.ofCelsius(62.5), Temperature.ofCelsius(60.0), Power.ZERO, Elwa2OperatingStatus.UNKNOWN, Instant.now()));
        when(vitodensAdapter.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(58.0));

        service.recordTemperatures();

        ArgumentCaptor<TemperatureLogEntry> captor = ArgumentCaptor.forClass(TemperatureLogEntry.class);
        verify(fileWriter).append(captor.capture());
        TemperatureLogEntry entry = captor.getValue();
        assertDoesNotThrow(() -> entry.getTimestamp().toString());
        assertEquals(62.5, entry.getHeatingRodTemperature().getCelsius());
        assertEquals(58.0, entry.getGasHeatingTemperature().getCelsius());
    }

    @Test
    void recordTemperatures_swallowsException_whenReadingTemperatureFails() {
        when(config.enabled()).thenReturn(true);
        when(elwa2Adapter.readMeasurements()).thenThrow(new RuntimeException("Modbus timeout"));

        assertDoesNotThrow(() -> service.recordTemperatures());

        verify(fileWriter, never()).append(any());
    }
}
