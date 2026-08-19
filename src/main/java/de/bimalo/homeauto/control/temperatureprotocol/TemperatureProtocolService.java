package de.bimalo.homeauto.control.temperatureprotocol;

import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.boundary.temperatureprotocol.TemperatureProtocolFileWriter;
import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.entity.TemperatureLogEntry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    private final TemperatureProtocolConfig config;
    private final Elwa2Adapter elwa2Adapter;
    private final VitodensAdapter vitodensAdapter;
    private final TemperatureProtocolFileWriter fileWriter;

    @Inject
    public TemperatureProtocolService(
            TemperatureProtocolConfig config,
            Elwa2Adapter elwa2Adapter,
            VitodensAdapter vitodensAdapter,
            TemperatureProtocolFileWriter fileWriter) {
        this.config = config;
        this.elwa2Adapter = elwa2Adapter;
        this.vitodensAdapter = vitodensAdapter;
        this.fileWriter = fileWriter;
    }

    @Scheduled(every = "5m")
    public void recordTemperatures() {
        if (!config.enabled()) {
            return;
        }

        try {
            TemperatureLogEntry entry = TemperatureLogEntry.builder()
                    .timestamp(Instant.now())
                    .heatingRodTemperature(elwa2Adapter.readMeasurements().currentTemperature())
                    .gasHeatingTemperature(vitodensAdapter.readStatus().currentTemperature())
                    .build();
            fileWriter.append(entry);
        } catch (Exception e) {
            log.error("Failed to record temperature protocol entry", e);
        }
    }
}
