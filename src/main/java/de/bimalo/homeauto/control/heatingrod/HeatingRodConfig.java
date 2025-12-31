package de.bimalo.homeauto.control.heatingrod;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for the heating rod system.
 */
@ConfigMapping(prefix = "heatingrod")
public interface HeatingRodConfig {

    /**
     * Modbus-spezifische Konfiguration.
     */
    ModbusConfig modbus();

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
