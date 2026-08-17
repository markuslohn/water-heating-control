package de.bimalo.homeauto.control.temperatureprotocol;

import de.bimalo.homeauto.boundary.temperatureprotocol.TemperatureProtocolFileWriter;
import de.bimalo.homeauto.control.gasheating.GasHeatingService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
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
    private final HeatingRodService heatingRodService;
    private final GasHeatingService gasHeatingService;
    private final TemperatureProtocolFileWriter fileWriter;

    @Inject
    public TemperatureProtocolService(
            TemperatureProtocolConfig config,
            HeatingRodService heatingRodService,
            GasHeatingService gasHeatingService,
            TemperatureProtocolFileWriter fileWriter) {
        this.config = config;
        this.heatingRodService = heatingRodService;
        this.gasHeatingService = gasHeatingService;
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
                    .heatingRodTemperature(heatingRodService.readTemperature1())
                    .gasHeatingTemperature(gasHeatingService.readHotWaterCurrentTemperature())
                    .build();
            fileWriter.append(entry);
        } catch (Exception e) {
            log.error("Failed to record temperature protocol entry", e);
        }
    }
}
