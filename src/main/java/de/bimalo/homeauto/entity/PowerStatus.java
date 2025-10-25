package de.bimalo.homeauto.entity;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Datenklasse für die wichtigsten Leistungsdaten des Batteriespeichers.
 */
@Getter
@ToString
public class PowerStatus {
    /** Zeitstempel der Messung */
    private final LocalDateTime timestamp;
    
    /** Aktuelle PV-Leistung in Watt */
    private final int powerProduction;
    
    /** Aktueller Hausverbrauch in Watt */
    private final int powerConsumption;
    
    /** Aktueller Ladezustand in Prozent (0-100) */
    private final int batteryStateOfCharge;
    
    @Builder
    public PowerStatus(int powerProduction, int powerConsumption, int batteryStateOfCharge) {
        this.timestamp = LocalDateTime.now();
        this.powerProduction = powerProduction;
        this.powerConsumption = powerConsumption;
        this.batteryStateOfCharge = batteryStateOfCharge;
    }
}