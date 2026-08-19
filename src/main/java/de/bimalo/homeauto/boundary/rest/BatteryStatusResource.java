package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.boundary.e3dc.E3dcAdapter;
import de.bimalo.homeauto.control.heatingcontrol.HeatingControlService;
import de.bimalo.homeauto.entity.BatteryStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for battery storage status information.
 */
@Slf4j
@Path("/api/battery")
@Produces(MediaType.APPLICATION_JSON)
public class BatteryStatusResource {

    @Inject
    E3dcAdapter e3dcAdapter;

    @Inject
    HeatingControlService heatingControlService;

    /**
     * Gets the current battery storage status.
     *
     * @return current battery status including production, consumption, battery
     *         power, grid power and SOC
     */
    @GET
    @Path("/status")
    public BatteryStatus getStatus() {
        log.debug("REST: Getting battery status");
        try {
            return e3dcAdapter.readStatus();
        } catch (RuntimeException ex) {
            return e3dcAdapter.getLastKnownStatus().orElseThrow(ServiceUnavailableException::new);
        }
    }

    /**
     * Gets the current battery priority status.
     *
     * @return true if battery priority is active, false if disabled by override
     */
    @GET
    @Path("/priority")
    public Response getBatteryPriorityStatus() {
        boolean active = heatingControlService.isBatteryPriorityActive();
        log.debug("REST: Getting battery priority status: {}", active);
        return Response.ok()
                .entity(String.format("{\"active\": %s}", active))
                .build();
    }

    /**
     * Toggles the battery priority override.
     * When disabled=true, battery priority is temporarily disabled until midnight.
     * When disabled=false, battery priority is re-enabled immediately.
     *
     * @param disabled true to disable battery priority, false to enable it
     * @return HTTP response indicating the new state
     */
    @POST
    @Path("/priority")
    public Response setBatteryPriority(@QueryParam("disabled") boolean disabled) {
        log.info("REST: Battery priority override requested: disabled={}", disabled);

        try {
            heatingControlService.setBatteryPriorityOverride(disabled);
            boolean active = heatingControlService.isBatteryPriorityActive();

            return Response.ok()
                    .entity(String.format("Battery priority is now %s", active ? "ACTIVE" : "DISABLED"))
                    .build();
        } catch (IllegalStateException e) {
            log.error("REST: Failed to set battery priority override: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(String.format("{\"error\": \"%s\"}", e.getMessage()))
                    .build();
        }
    }
}
