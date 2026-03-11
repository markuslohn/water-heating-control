package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.control.heatingrod.HeatingRodService;
import de.bimalo.homeauto.entity.HeatingStatus;
import de.bimalo.homeauto.entity.Power;
import de.bimalo.homeauto.entity.Temperature;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for heating rod control and status.
 */
@Slf4j
@Path("/api/heatingrod")
@Produces(MediaType.APPLICATION_JSON)
public class HeatingControlResource {

    @Inject
    HeatingRodService heatingRodService;

    @Inject
    HeatingControlService heatingControlService;

    /**
     * Gets the current heating rod status.
     *
     * @return current heating status including active state, power, temperatures and manual mode
     */
    @GET
    @Path("/status")
    public HeatingStatus getStatus() {
        log.debug("REST: Getting heating status");

        Power power = heatingRodService.readPower();
        Temperature currentTemp = heatingRodService.readTemperature1();
        Temperature targetTemp = heatingRodService.readTargetTemperature();

        return HeatingStatus.builder()
                .active(power.getWatts() > 0)
                .power(power)
                .currentTemperature(currentTemp)
                .targetTemperature(targetTemp)
                .manualMode(heatingControlService.isManualModeActive())
                .build();
    }

    /**
     * Manually controls the heating rod power.
     * When watts > 0, manual mode is activated and automatic control is suspended.
     * When watts = 0, manual mode is deactivated and automatic control resumes.
     *
     * @param watts the desired power in watts (0 to stop heating and resume automatic control)
     * @return HTTP response indicating success or failure
     */
    @POST
    @Path("/control")
    public Response control(@QueryParam("watts") int watts) {
        log.info("REST: Manual heating control requested with {} W", watts);

        try {
            if (watts > 0) {
                heatingControlService.activateManualMode();
            } else {
                heatingControlService.deactivateManualMode();
            }
            heatingRodService.adjustHeating(Power.ofWatts(watts));
            return Response.ok()
                    .entity(String.format("Heating adjusted to %d W (manual mode: %s)",
                            watts, watts > 0 ? "active" : "inactive"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
