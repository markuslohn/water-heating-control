package de.bimalo.homeauto.control.temperatureprotocol;

import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.temperatureprotocol.TemperatureProtocolFileWriter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.entity.Temperature;
import de.bimalo.homeauto.entity.TemperatureLogEntry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically records the heating rod and gas heating hot water temperatures
 * to a daily CSV protocol, to help understand how the two sensors relate.
 * Only active while {@code tempprotocol.enabled} is true.
 */
@Slf4j
@ApplicationScoped
public class TemperatureProtocolService {

    /**
     * How old a last-known status may be before it's reused instead of
     * triggering a fresh Modbus read. Comfortably above the 40-50s automatic
     * and manual control cycles, so their reads are reused in the common
     * case, but well under this service's own 5-minute interval, so a
     * dedicated read still happens if nothing else has read recently (e.g.
     * automatic control disabled and manual mode never started).
     */
    private static final Duration FRESHNESS_WINDOW = Duration.ofMinutes(2);

    private final TemperatureProtocolConfig config;
    private final Elwa2Adapter elwa2Adapter;
    private final VitodensAdapter vitodensAdapter;
    private final TemperatureProtocolFileWriter fileWriter;
    private final Clock clock;

    @Inject
    public TemperatureProtocolService(
            TemperatureProtocolConfig config,
            Elwa2Adapter elwa2Adapter,
            VitodensAdapter vitodensAdapter,
            TemperatureProtocolFileWriter fileWriter) {
        this(config, elwa2Adapter, vitodensAdapter, fileWriter, Clock.systemUTC());
    }

    TemperatureProtocolService(
            TemperatureProtocolConfig config,
            Elwa2Adapter elwa2Adapter,
            VitodensAdapter vitodensAdapter,
            TemperatureProtocolFileWriter fileWriter,
            Clock clock) {
        this.config = config;
        this.elwa2Adapter = elwa2Adapter;
        this.vitodensAdapter = vitodensAdapter;
        this.fileWriter = fileWriter;
        this.clock = clock;
    }

    @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void recordTemperatures() {
        if (!config.enabled()) {
            return;
        }

        try {
            TemperatureLogEntry entry = TemperatureLogEntry.builder()
                    .timestamp(Instant.now())
                    .heatingRodTemperature(heatingRodTemperature())
                    .gasHeatingTemperature(gasHeatingTemperature())
                    .build();
            fileWriter.append(entry);
        } catch (Exception e) {
            log.error("Failed to record temperature protocol entry", e);
        }
    }

    private Temperature heatingRodTemperature() {
        return elwa2Adapter.getLastKnownStatus()
                .filter(status -> !status.isOlderThan(FRESHNESS_WINDOW, clock))
                .orElseGet(elwa2Adapter::readStatus)
                .currentTemperature();
    }

    private Temperature gasHeatingTemperature() {
        return vitodensAdapter.getLastKnownStatus()
                .filter(status -> !status.isOlderThan(FRESHNESS_WINDOW, clock))
                .orElseGet(vitodensAdapter::readStatus)
                .currentTemperature();
    }
}
