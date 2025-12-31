package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.control.battery.BatteryStorageService;
import de.bimalo.homeauto.entity.BatteryStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for battery storage status information.
 */
@Slf4j
@Path("/api/battery")
@Produces(MediaType.APPLICATION_JSON)
public class BatteryStatusResource {

    @Inject
    BatteryStorageService batteryStorageService;

    /**
     * Gets the current battery storage status.
     *
     * @return current battery status including production, consumption, battery power, grid power and SOC
     */
    @GET
    @Path("/status")
    public BatteryStatus getStatus() {
        log.debug("REST: Getting battery status");
        return batteryStorageService.getCurrentStatus();
    }
}
