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
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for heating rod control and status.
 */
@Slf4j
@Path("/api/heatingrod")
@Produces(MediaType.APPLICATION_JSON)
public class HeatingControlResource {

    @Inject
    Elwa2Adapter elwa2Adapter;

    @Inject
    HeatingControlService heatingControlService;

    /**
     * Gets the current heating rod status.
     *
     * @return current heating status including active state, power, temperatures
     *         and manual mode
     */
    @GET
    @Path("/status")
    public HeatingStatus getStatus() {
        log.debug("REST: Getting heating status");

        HeatingRodStatus measurements;
        try {
            measurements = elwa2Adapter.readStatus();
        } catch (RuntimeException ex) {
            measurements = elwa2Adapter.getLastKnownStatus().orElseThrow(ServiceUnavailableException::new);
        }

        Power power = measurements.currentPower();
        Temperature currentTemp = measurements.currentTemperature();
        Temperature targetTemp = measurements.targetTemperature();
        Season currentSeason = heatingControlService.getCurrentSeason();

        return HeatingStatus.builder()
                .active(power.getWatts() > 0)
                .power(power)
                .currentTemperature(currentTemp)
                .targetTemperature(targetTemp)
                .manualMode(heatingControlService.isManualModeActive())
                .season(currentSeason.getDisplayName())
                .seasonEmoji(currentSeason.getEmoji())
                .seasonEnabled(heatingControlService.isCurrentSeasonEnabled())
                .build();
    }
}
