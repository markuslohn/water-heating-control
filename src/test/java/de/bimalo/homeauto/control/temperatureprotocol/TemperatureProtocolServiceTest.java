package de.bimalo.homeauto.control.temperatureprotocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.temperatureprotocol.TemperatureProtocolFileWriter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.entity.GasHeatingStatus;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.TemperatureLogEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemperatureProtocolServiceTest {

    private static final Instant NOW = Instant.parse("2024-01-01T12:00:00Z");

    @Mock
    private TemperatureProtocolConfig config;

    @Mock
    private Elwa2Adapter elwa2Adapter;

    @Mock
    private VitodensAdapter vitodensAdapter;

    @Mock
    private TemperatureProtocolFileWriter fileWriter;

    private Clock clock;
    private TemperatureProtocolService service;

    @BeforeEach
    void setUp() {
        // A mocked (rather than fixed) Clock lets individual tests control how old
        // a last-known status is relative to "now". A package-private constructor
        // wires it explicitly rather than relying on @InjectMocks, which would pick
        // this constructor over the @Inject one and inject a null Clock.
        clock = mock(Clock.class);
        lenient().when(clock.instant()).thenReturn(NOW);
        service = new TemperatureProtocolService(config, elwa2Adapter, vitodensAdapter, fileWriter, clock);
    }

    @Test
    void recordTemperatures_doesNothing_whenDisabled() {
        when(config.enabled()).thenReturn(false);

        service.recordTemperatures();

        verify(elwa2Adapter, never()).readStatus();
        verify(fileWriter, never()).append(any());
    }

    @Test
    void recordTemperatures_writesEntryWithBothTemperatures_whenEnabled() {
        when(config.enabled()).thenReturn(true);
        when(elwa2Adapter.readStatus()).thenReturn(
                new HeatingRodStatus(Temperature.ofCelsius(62.5), Temperature.ofCelsius(60.0), Power.ZERO,
                        Instant.now()));
        when(vitodensAdapter.readStatus()).thenReturn(
                GasHeatingStatus.builder()
                        .active(false)
                        .currentTemperature(Temperature.ofCelsius(58.0))
                        .targetTemperature(Temperature.ofCelsius(55.0))
                        .measuredAt(Instant.now())
                        .build());

        service.recordTemperatures();

        ArgumentCaptor<TemperatureLogEntry> captor = ArgumentCaptor.forClass(TemperatureLogEntry.class);
        verify(fileWriter).append(captor.capture());
        TemperatureLogEntry entry = captor.getValue();
        assertDoesNotThrow(() -> entry.getTimestamp().toString());
        assertEquals(62.5, entry.getHeatingRodTemperature().celsius());
        assertEquals(58.0, entry.getGasHeatingTemperature().celsius());
    }

    @Test
    void recordTemperatures_swallowsException_whenReadingTemperatureFails() {
        when(config.enabled()).thenReturn(true);
        when(elwa2Adapter.readStatus()).thenThrow(new RuntimeException("Modbus timeout"));

        assertDoesNotThrow(() -> service.recordTemperatures());

        verify(fileWriter, never()).append(any());
    }

    // ==================== Reuse of recently-read status (O4) ====================

    @Test
    void recordTemperatures_reusesRecentLastKnownStatus_insteadOfReadingAgain() {
        when(config.enabled()).thenReturn(true);
        when(elwa2Adapter.getLastKnownStatus()).thenReturn(Optional.of(
                new HeatingRodStatus(Temperature.ofCelsius(62.5), Temperature.ofCelsius(60.0), Power.ZERO,
                        NOW.minus(Duration.ofSeconds(45)))));
        when(vitodensAdapter.getLastKnownStatus()).thenReturn(Optional.of(
                GasHeatingStatus.builder()
                        .active(false)
                        .currentTemperature(Temperature.ofCelsius(58.0))
                        .targetTemperature(Temperature.ofCelsius(55.0))
                        .measuredAt(NOW.minus(Duration.ofSeconds(50)))
                        .build()));

        service.recordTemperatures();

        verify(elwa2Adapter, never()).readStatus();
        verify(vitodensAdapter, never()).readStatus();

        ArgumentCaptor<TemperatureLogEntry> captor = ArgumentCaptor.forClass(TemperatureLogEntry.class);
        verify(fileWriter).append(captor.capture());
        assertEquals(62.5, captor.getValue().getHeatingRodTemperature().celsius());
        assertEquals(58.0, captor.getValue().getGasHeatingTemperature().celsius());
    }

    @Test
    void recordTemperatures_readsAgain_whenLastKnownStatusIsStale() {
        when(config.enabled()).thenReturn(true);
        when(elwa2Adapter.getLastKnownStatus()).thenReturn(Optional.of(
                new HeatingRodStatus(Temperature.ofCelsius(40.0), Temperature.ofCelsius(60.0), Power.ZERO,
                        NOW.minus(Duration.ofMinutes(3)))));
        when(elwa2Adapter.readStatus()).thenReturn(
                new HeatingRodStatus(Temperature.ofCelsius(62.5), Temperature.ofCelsius(60.0), Power.ZERO, NOW));
        when(vitodensAdapter.getLastKnownStatus()).thenReturn(Optional.of(
                GasHeatingStatus.builder()
                        .active(false)
                        .currentTemperature(Temperature.ofCelsius(30.0))
                        .targetTemperature(Temperature.ofCelsius(55.0))
                        .measuredAt(NOW.minus(Duration.ofMinutes(3)))
                        .build()));
        when(vitodensAdapter.readStatus()).thenReturn(
                GasHeatingStatus.builder()
                        .active(false)
                        .currentTemperature(Temperature.ofCelsius(58.0))
                        .targetTemperature(Temperature.ofCelsius(55.0))
                        .measuredAt(NOW)
                        .build());

        service.recordTemperatures();

        verify(elwa2Adapter).readStatus();
        verify(vitodensAdapter).readStatus();

        ArgumentCaptor<TemperatureLogEntry> captor = ArgumentCaptor.forClass(TemperatureLogEntry.class);
        verify(fileWriter).append(captor.capture());
        assertEquals(62.5, captor.getValue().getHeatingRodTemperature().celsius());
        assertEquals(58.0, captor.getValue().getGasHeatingTemperature().celsius());
    }

    @Test
    void recordTemperatures_readsAgain_whenNothingKnownYet() {
        when(config.enabled()).thenReturn(true);
        when(elwa2Adapter.readStatus()).thenReturn(
                new HeatingRodStatus(Temperature.ofCelsius(62.5), Temperature.ofCelsius(60.0), Power.ZERO, NOW));
        when(vitodensAdapter.readStatus()).thenReturn(
                GasHeatingStatus.builder()
                        .active(false)
                        .currentTemperature(Temperature.ofCelsius(58.0))
                        .targetTemperature(Temperature.ofCelsius(55.0))
                        .measuredAt(NOW)
                        .build());

        service.recordTemperatures();

        verify(elwa2Adapter).readStatus();
        verify(vitodensAdapter).readStatus();
    }
}
