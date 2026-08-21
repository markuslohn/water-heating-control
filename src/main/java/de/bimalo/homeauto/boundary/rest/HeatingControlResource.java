package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.boundary.elwa2.Elwa2Adapter;
import de.bimalo.homeauto.entity.HeatingRodStatus;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.entity.Season;
import de.bimalo.homeauto.entity.HeatingStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for heating rod control and status.
 */
@Slf4j
@Path("/api/heatingrod")
@Produces(MediaType.APPLICATION_JSON)
public class HeatingControlResource {

    private final Elwa2Adapter elwa2Adapter;
    private final HeatingControlService heatingControlService;
    private final RestStatusConfig restStatusConfig;

    @Inject
    public HeatingControlResource(
            Elwa2Adapter elwa2Adapter,
            HeatingControlService heatingControlService,
            RestStatusConfig restStatusConfig) {
        this.elwa2Adapter = elwa2Adapter;
        this.heatingControlService = heatingControlService;
        this.restStatusConfig = restStatusConfig;
    }

    /**
     * Gets the current heating rod status. Falls back to the last known
     * status if the live read fails, marked {@code stale=true} - unless that
     * status is itself too old, in which case this returns 503.
     *
     * @return current heating status including active state, power, temperatures
     *         and manual mode
     */
    @GET
    @Path("/status")
    public HeatingStatus getStatus() {
        log.debug("REST: Getting heating status");

        StatusFallback.ResolvedStatus<HeatingRodStatus> resolved = StatusFallback.resolve(
                elwa2Adapter::readStatus,
                elwa2Adapter::getLastKnownStatus,
                HeatingRodStatus::measuredAt,
                restStatusConfig.maxCacheAge(),
                Clock.systemUTC());
        HeatingRodStatus status = resolved.status();

        Power power = status.currentPower();
        Temperature currentTemp = status.currentTemperature();
        Temperature targetTemp = status.targetTemperature();
        Season currentSeason = heatingControlService.getCurrentSeason();

        return HeatingStatus.builder()
                .active(power.watts() > 0)
                .power(power)
                .currentTemperature(currentTemp)
                .targetTemperature(targetTemp)
                .manualMode(heatingControlService.isManualModeActive())
                .season(currentSeason.getDisplayName())
                .seasonEmoji(currentSeason.getEmoji())
                .seasonEnabled(heatingControlService.isCurrentSeasonEnabled())
                .stale(resolved.stale())
                .measuredAt(status.measuredAt())
                .build();
    }
}
