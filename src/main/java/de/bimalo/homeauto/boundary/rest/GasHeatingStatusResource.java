package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.entity.GasHeatingStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for gas heating status information.
 */
@Slf4j
@Path("/api/gasheating")
@Produces(MediaType.APPLICATION_JSON)
public class GasHeatingStatusResource {

    @Inject
    VitodensAdapter vitodensAdapter;

    /**
     * Gets the current gas heating status.
     *
     * @return current gas heating status including active state and temperatures
     */
    @GET
    @Path("/status")
    public GasHeatingStatus getStatus() {
        log.debug("REST: Getting gas heating status");
        return GasHeatingStatus.builder()
                .active(vitodensAdapter.isHeatingActive())
                .currentTemperature(vitodensAdapter.readHotWaterCurrentTemperature())
                .targetTemperature(vitodensAdapter.readHotWaterTargetTemperature())
                .build();
    }
}
