package de.bimalo.homeauto.boundary.viessman;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum VitodensRegister {

    STATUS(10009, "Verbindungsstatus zum Gerät", 1, 1),
    EXTERNAL_REQUEST(40003, "Externe Anforderung", 1, 1),
    EXTERNAL_REQUEST_STATUS(30001, "Externe Anforderung Status", 1, 0),
    HOT_WATER_TARGET_TEMPERATUR(40004, "Warmwassertermperatur-Sollwert", 0.1, 10),
    HOT_WATER_HEATING_PROGRAMM_TARGET(40005, "Warmwasser Betriebsprogramm: Soll", 1, 1),
    HOT_WATER_HEATING_PROGRAMM_CURRENT(40005, "Warmwasser Betriebsprogramm: Ist", 1, 0),
    HOT_WATER_CURRENT_TEMPERATURE(30022, "Warmwassertemperatur 271.0", 0.1, 0),
    OUTSIDE_TEMPERATURE(30009, "Aussentemperatur 274.0", 0.1, 0),
    HOT_WATER_STATUS(30024, "Warmwasserstatus 1659.1", 1, 0),
    HOT_WATER_GAS_CONSUMPTION_TODAY(30066, "Warmwasser Gasverbrauch: Heute", 0.1, 0),
    HOT_WATER_GAS_CONSUMPTION_THIS_MONTH(30068, "Warmwasser Gasverbrauch: Diesen Monat", 0.1, 0);

    private final int handbookAddress;
    private final String description;
    private final double readFactor;
    private final double writeFactor;

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
