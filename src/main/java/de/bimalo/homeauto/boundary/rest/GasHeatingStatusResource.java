package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.boundary.viessman.VitodensAdapter;
import de.bimalo.homeauto.entity.GasHeatingStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for gas heating status information.
 */
@Slf4j
@Path("/api/gasheating")
@Produces(MediaType.APPLICATION_JSON)
public class GasHeatingStatusResource {

    private final VitodensAdapter vitodensAdapter;
    private final RestStatusConfig restStatusConfig;

    @Inject
    public GasHeatingStatusResource(VitodensAdapter vitodensAdapter, RestStatusConfig restStatusConfig) {
        this.vitodensAdapter = vitodensAdapter;
        this.restStatusConfig = restStatusConfig;
    }

    /**
     * Gets the current gas heating status. Falls back to the last known
     * status if the live read fails, marked {@code stale=true} - unless that
     * status is itself too old, in which case this returns 503.
     *
     * @return current gas heating status including active state and temperatures
     */
    @GET
    @Path("/status")
    public GasHeatingStatusResponse getStatus() {
        log.debug("REST: Getting gas heating status");
        StatusFallback.ResolvedStatus<GasHeatingStatus> resolved = StatusFallback.resolve(
                vitodensAdapter::readStatus,
                vitodensAdapter::getLastKnownStatus,
                GasHeatingStatus::measuredAt,
                restStatusConfig.maxCacheAge(),
                Clock.systemUTC());
        return GasHeatingStatusResponse.of(resolved.status(), resolved.stale());
    }
}
