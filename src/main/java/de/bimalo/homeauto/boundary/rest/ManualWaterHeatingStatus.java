package de.bimalo.homeauto.boundary.rest;

import de.bimalo.homeauto.entity.HeatingSource;
import lombok.Builder;
import lombok.Getter;

/**
 * Data class for the manual water heating status.
 */
@Getter
@Builder
public final class ManualWaterHeatingStatus {

    private final boolean active;
    private final HeatingSource source;

    @Override
    public String toString() {
        return String.format("ManualWaterHeatingStatus[active=%s, source=%s]", active, source);
    }
}
