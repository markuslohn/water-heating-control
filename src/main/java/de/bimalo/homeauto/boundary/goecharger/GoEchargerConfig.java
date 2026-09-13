package de.bimalo.homeauto.boundary.goecharger;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for go-eCharger wallbox.
 */
@ConfigMapping(prefix = "goecharger")
public interface GoEchargerConfig {

    ModbusConfig modbus();

    interface ModbusConfig {
        /**
         * IP or hostname of the go-eCharger wallbox.
         */
        @NotBlank
        String host();

        /**
         * TCP-Port des go-eCharger wallbox.
         */
        @Min(1)
        @Max(65535)
        @WithDefault("502")
        int port();
    }
}
