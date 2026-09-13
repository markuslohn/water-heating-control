package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.boundary.goecharger.GoEchargerAdapter;
import de.bimalo.homeauto.entity.WallboxStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for wallbox (electric vehicle charging) status information.
 */
@Slf4j
@Path("/api/wallbox")
@Produces(MediaType.APPLICATION_JSON)
public class WallboxStatusResource {

    private final GoEchargerAdapter goEchargerAdapter;
    private final RestStatusConfig restStatusConfig;

    @Inject
    public WallboxStatusResource(GoEchargerAdapter goEchargerAdapter, RestStatusConfig restStatusConfig) {
        this.goEchargerAdapter = goEchargerAdapter;
        this.restStatusConfig = restStatusConfig;
    }

    /**
     * Gets the current wallbox charging status. Falls back to the last known
     * status if the live read fails, marked {@code stale=true} - unless that
     * status is itself too old, in which case this returns 503.
     *
     * @return current wallbox status including charging state and charging power
     */
    @GET
    @Path("/status")
    public WallboxStatusResponse getStatus() {
        log.debug("REST: Getting wallbox status");
        StatusFallback.ResolvedStatus<WallboxStatus> resolved = StatusFallback.resolve(
                goEchargerAdapter::readStatus,
                goEchargerAdapter::getLastKnownStatus,
                WallboxStatus::measuredAt,
                restStatusConfig.maxCacheAge(),
                Clock.systemUTC());
        return WallboxStatusResponse.of(resolved.status(), resolved.stale());
    }
}
