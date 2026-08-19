package de.bimalo.homeauto.boundary.elwa2;

import java.time.Duration;

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
     * Modbus-spezifische Konfiguration.
     */
    ModbusConfig modbus();

    @WithDefault("40s")
    Duration powerCommandTimeoutFallback();

    /**
     * Interval at which it is checked whether the heating power request needs to
     * be refreshed. The ELWA2 reverts to standby on its own if the power request
     * isn't refreshed within its own power timeout (see
     * {@link Elwa2ModbusClient#readPowerTimeout()}).
     * Supports duration expressions like "10s", "1m".
     */
    @WithDefault("10s")
    Duration keepAliveCheckInterval();

    /**
     * Modbus Konfiguration.
     */
    interface ModbusConfig {
        /**
         * IP-Adresse oder Hostname des E3/DC Systems.
         */
        @NotBlank
        String host();

        /**
         * TCP-Port des E3/DC Systems.
         */
        @Min(1)
        @Max(65535)
        @WithDefault("502")
        int port();
    }
}
