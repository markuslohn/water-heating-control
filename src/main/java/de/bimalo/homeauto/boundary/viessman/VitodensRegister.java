package de.bimalo.homeauto.boundary.viessman;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum VitodensRegister {

    HOT_WATER_TEMPERATURE(30022, "Warmwassertemperatur 271.0", 10.0),
    OUTSIDE_TEMPERATURE(30009, "Aussentemperatur 274.0", 10.0),
    HOT_WATER_STATUS(30024, "Warmwasserstatus 1659.1", 0),
    BETRIEBSSTUNDEN_WARMEERZEUGER(30016, "Betriebsstunden Wärmeerzeuger", 1.0),
    XXX(30136, "xxx", 1.0);

    // private static final int OFFSET = 40001; // Modbus Holding Register Offset

    private final int handbookAddress;
    private final String description;
    private final double scaleFactor;

    /**
     * Returns the address for the modbus request (with offset correction)
     */
    public int getAddress() {
        return handbookAddress;
    }

    @Override
    public String toString() {
        return String.format("%s: Register %d (Request-Adresse: %d) - %s",
                name(), handbookAddress, getAddress(), description);
    }

}
