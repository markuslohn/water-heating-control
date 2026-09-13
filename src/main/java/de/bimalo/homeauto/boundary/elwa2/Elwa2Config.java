package de.bimalo.homeauto.boundary.elwa2;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for the heating rod system.
 */
@ConfigMapping(prefix = "elwa2")
public interface Elwa2Config {

    /**
     * Modbus-specific configuration.
     */
    ModbusConfig modbus();

    /**
     * Modbus configuration.
     */
    interface ModbusConfig {
        /**
         * IP address or hostname of the ELWA2 heating rod.
         */
        @NotBlank
        String host();

        /**
         * TCP port of the ELWA2 heating rod.
         */
        @Min(1)
        @Max(65535)
        @WithDefault("502")
        int port();
    }
}
