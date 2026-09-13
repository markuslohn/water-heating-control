package de.bimalo.homeauto.boundary.e3dc;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for the E3/DC battery storage system.
 */
@ConfigMapping(prefix = "e3dc")
public interface E3dcConfig {

    /**
     * Modbus-specific configuration.
     */
    ModbusConfig modbus();

    /**
     * Modbus configuration.
     */
    interface ModbusConfig {
        /**
         * IP address or hostname of the E3/DC system.
         */
        @NotBlank
        String host();

        /**
         * TCP port of the E3/DC system.
         */
        @Min(1)
        @Max(65535)
        @WithDefault("502")
        int port();
    }
}
