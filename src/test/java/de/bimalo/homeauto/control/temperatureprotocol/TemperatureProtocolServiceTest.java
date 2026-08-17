package de.bimalo.homeauto.control.temperatureprotocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.temperatureprotocol.TemperatureProtocolFileWriter;
import de.bimalo.homeauto.control.gasheating.GasHeatingService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.TemperatureLogEntry;
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
    private HeatingRodService heatingRodService;

    @Mock
    private GasHeatingService gasHeatingService;

    @Mock
    private TemperatureProtocolFileWriter fileWriter;

    @InjectMocks
    private TemperatureProtocolService service;

    @Test
    void recordTemperatures_doesNothing_whenDisabled() {
        when(config.enabled()).thenReturn(false);

        service.recordTemperatures();

        verify(heatingRodService, never()).readTemperature1();
        verify(fileWriter, never()).append(any());
    }

    @Test
    void recordTemperatures_writesEntryWithBothTemperatures_whenEnabled() {
        when(config.enabled()).thenReturn(true);
        when(heatingRodService.readTemperature1()).thenReturn(Temperature.ofCelsius(62.5));
        when(gasHeatingService.readHotWaterCurrentTemperature()).thenReturn(Temperature.ofCelsius(58.0));

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
        when(heatingRodService.readTemperature1()).thenThrow(new RuntimeException("Modbus timeout"));

        assertDoesNotThrow(() -> service.recordTemperatures());

        verify(fileWriter, never()).append(any());
    }
}
