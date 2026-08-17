package de.bimalo.homeauto.control.temperatureprotocol;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the daily temperature protocol.
 */
@ConfigMapping(prefix = "tempprotocol")
public interface TemperatureProtocolConfig {

    /**
     * Whether the daily temperature protocol (heating rod vs. gas heating hot
     * water temperature) is recorded.
     */
    @WithDefault("false")
    boolean enabled();
}
