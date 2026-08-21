package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.entity.ManualHeatingState;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for controlling manual water heating mode.
 */
@Slf4j
@Path("/api/manual-water-heating")
@Produces(MediaType.APPLICATION_JSON)
public class ManualWaterHeatingResource {

    private final HeatingControlService heatingControlService;

    @Inject
    public ManualWaterHeatingResource(HeatingControlService heatingControlService) {
        this.heatingControlService = heatingControlService;
    }

    /**
     * Gets the current manual water heating status.
     *
     * @return current status including active state and heating source
     */
    @GET
    @Path("/status")
    public ManualWaterHeatingStatus getStatus() {
        log.debug("REST: Getting manual water heating status");
        return toStatus(heatingControlService.getManualStatus());
    }

    /**
     * Starts manual water heating mode.
     */
    @POST
    @Path("/start")
    public ManualWaterHeatingStatus start() {
        log.info("REST: Starting manual water heating mode");
        heatingControlService.activateManualHeating();
        return toStatus(heatingControlService.getManualStatus());
    }

    /**
     * Stops manual water heating mode.
     */
    @POST
    @Path("/stop")
    public ManualWaterHeatingStatus stop() {
        log.info("REST: Stopping manual water heating mode");
        heatingControlService.deactivateManualHeating();
        return toStatus(heatingControlService.getManualStatus());
    }

    private static ManualWaterHeatingStatus toStatus(ManualHeatingState state) {
        return ManualWaterHeatingStatus.builder()
                .active(state.active())
                .source(state.source())
                .build();
    }
}
