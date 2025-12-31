package de.bimalo.homeauto.entity;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeviceInfo {
    String manufacturer;
    String model;
    String serialNumber;
    String firmwareVersion;

    @Override
    public String toString() {
        return String.format("%s von %s mit SN %s (%s)",
            model,
            manufacturer,
            serialNumber,
            firmwareVersion);
    }
}
