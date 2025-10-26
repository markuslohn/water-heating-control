package de.bimalo.homeauto.control.battery;

/**
 * Konstanten-Klasse für die Modbus-Register des E3/DC Batteriespeichers im Simple Mode.
 * Basierend auf der E3/DC Modbus TCP Spezifikation.
 */
public final class BatteryModbusRegisters {
    private BatteryModbusRegisters() {
        // Verhindert Instanziierung
    }

    // Block 1: Identifikation (40001-40067)
    public static final int MAGIC_BYTE = 40001;             // Magic Byte - S10 ModBus ID (Immer 0xE3DC)
    public static final int FIRMWARE_VERSION = 40002;       // S10 ModBus-Firmware-Version (UInt8+UInt8)
    public static final int REGISTER_COUNT = 40003;         // Anzahl unterstützter Register
    public static final int MANUFACTURER = 40004;           // Hersteller: "E3/DC GmbH" (16 Register String)
    public static final int MODEL = 40020;                  // Modell, z.B.: "S10 E AIO" (16 Register String)
    public static final int SERIAL_NUMBER = 40036;          // Seriennummer, z.B.: "S10-12345678912" (16 Register String)
    public static final int FIRMWARE_RELEASE = 40052;       // S10 Firmware Release, z.B.: "S10-2015_08" (16 Register String)

    // Block 2: Leistungsdaten (40068-40084)
    public static final int PV_POWER = 40068;              // Photovoltaik-Leistung in Watt (Int32)
    public static final int BATTERY_POWER = 40070;         // Batterie-Leistung in Watt, negativ = Entladung (Int32)
    public static final int HOME_POWER = 40072;            // Hausverbrauchs-Leistung in Watt (Int32)
    public static final int GRID_POWER = 40074;            // Netzübergabepunkt-Leistung in Watt, negativ = Einspeisung (Int32)
    public static final int ADD_POWER = 40076;             // Leistung aller zusätzlichen Einspeiser in Watt (Int32)
    public static final int WALLBOX_POWER = 40078;         // Leistung der Wallbox in Watt (Int32)
    public static final int WALLBOX_SOLAR_POWER = 40080;   // Solarleistung für Wallbox in Watt (Int32)
    public static final int AUTARKY_CONSUMPTION = 40082;   // Autarkie und Eigenverbrauch in Prozent (UInt8+UInt8)
    public static final int BATTERY_SOC = 40083;           // Batterie-SOC in Prozent (UInt16)
    public static final int EMERGENCY_POWER_STATUS = 40084; // Emergency-Power Status (UInt16)
    public static final int EMS_STATUS = 40085;            // Energy Management System Status (UInt16)
    public static final int EMS_REMOTE_CONTROL = 40086;    // EMS Remote Control (Int16)
    public static final int EMS_CTRL = 40087;              // EMS Control (UInt16)
    public static final int WALLBOX_0_CTRL = 40088;        // Wallbox 0 Control (UInt16, Read/Write)

    /**
     * Status-Werte für das Emergency-Power Register (40084)
     */
    public static final class EmergencyPowerStatus {
        private EmergencyPowerStatus() {
            // Verhindert Instanziierung
        }

        /** Notstrom wird nicht unterstützt (ältere Gerätegeneration, z.B. S10-SP40, S10-P5002) */
        public static final int NOT_SUPPORTED = 0;

        /** Notstrom aktiv (Ausfall des Stromnetzes) */
        public static final int ACTIVE = 1;

        /** Notstrom nicht aktiv */
        public static final int NOT_ACTIVE = 2;

        /** Notstrom nicht verfügbar */
        public static final int NOT_AVAILABLE = 3;

        /** Motorschalter des S10 E nicht in korrekter Position (manuell abgeschaltet oder nicht eingeschaltet) */
        public static final int MOTOR_SWITCH_ERROR = 4;

        /**
         * Konvertiert den numerischen Status in einen beschreibenden String
         *
         * @param status Der Status-Code aus dem Register
         * @return Beschreibender String für den Status
         */
        public static String toString(int status) {
            return switch (status) {
                case NOT_SUPPORTED -> "Notstrom nicht unterstützt";
                case ACTIVE -> "Notstrom aktiv (Netzausfall)";
                case NOT_ACTIVE -> "Notstrom nicht aktiv";
                case NOT_AVAILABLE -> "Notstrom nicht verfügbar";
                case MOTOR_SWITCH_ERROR -> "Motorschalter nicht in korrekter Position";
                default -> "Unbekannter Status: " + status;
            };
        }
    }
}
