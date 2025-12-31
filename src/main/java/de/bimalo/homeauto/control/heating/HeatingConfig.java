package de.bimalo.homeauto.control.heating;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for the heating system.
 */
@ConfigMapping(prefix = "heating")
public interface HeatingConfig {

    /**
     * Modbus-spezifische Konfiguration.
     */
    ModbusConfig modbus();

    /**
     * Modbus Konfiguration.
     */
    interface ModbusConfig {
        /**
         * IP-address or hostname of the heating system.
         */
        @NotBlank
        String host();

        /**
         * TCP-port of the heating system.
         */
        @Min(1)
        @Max(65535)
        @WithDefault("502")
        int port();
    }
}
