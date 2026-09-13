package de.bimalo.homeauto.boundary.rest;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/**
 * Configuration shared by all REST status endpoints that fall back to a
 * device's last known status when a live read fails.
 */
@ConfigMapping(prefix = "reststatus")
public interface RestStatusConfig {

    /**
     * Maximum age of a last-known status that may still be served as a
     * fallback (marked {@code stale=true}) when a live read fails. Beyond
     * this age, the endpoint returns 503 instead of stale data.
     */
    @WithDefault("2m")
    Duration maxCacheAge();
}
