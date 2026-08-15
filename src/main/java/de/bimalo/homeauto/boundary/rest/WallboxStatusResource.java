package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.control.wallbox.WallboxService;
import de.bimalo.homeauto.entity.WallboxStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for wallbox (electric vehicle charging) status information.
 */
@Slf4j
@Path("/api/wallbox")
@Produces(MediaType.APPLICATION_JSON)
public class WallboxStatusResource {

    @Inject
    WallboxService wallboxService;

    /**
     * Gets the current wallbox charging status.
     *
     * @return current wallbox status including charging state and charging power
     */
    @GET
    @Path("/status")
    public WallboxStatus getStatus() {
        log.debug("REST: Getting wallbox status");
        return WallboxStatus.builder()
                .charging(wallboxService.isCharging())
                .chargingPower(wallboxService.readCurrentChargingPower())
                .build();
    }
}
