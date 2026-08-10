package de.bimalo.homeauto.control.gasheating;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for the heating system.
 */
@ConfigMapping(prefix = "gasheating")
public interface GasHeatingConfig {

    /**
     * Modbus-spezifische Konfiguration.
     */
    ModbusConfig modbus();

    /**
     * Interval at which the external Modbus request is refreshed while gas heating
     * is active. The Vitodens falls back to internal control if this register isn't
     * refreshed periodically. Supports duration expressions like "1m", "30s".
     */
    @WithDefault("2m")
    String keepAliveInterval();

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
