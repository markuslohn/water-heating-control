package de.bimalo.homeauto.control.battery;

import de.bimalo.homeauto.boundary.modbus.ModbusClient;

import de.bimalo.homeauto.entity.PowerStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


/**
 * Service-Klasse für den E3/DC Batteriespeicher im Simple Mode.
 * Stellt die wichtigsten Leistungsdaten des Batteriespeichers zur Verfügung.
 */
@ApplicationScoped
public class BatteryStorageService {
    
    private final ModbusClient modbusClient;
    private final BatteryStorageConfig config;
    
    @Inject
    public BatteryStorageService(BatteryStorageConfig config) {
        this.config = config;
        this.modbusClient = createModbusClient(
            config.modbus().host(),
            config.modbus().port()
        );
    }
    
    /**
     * Erstellt einen neuen ModbusClient.
     * Diese Methode wurde extrahiert, um das Testen zu erleichtern.
     */
    protected ModbusClient createModbusClient(String host, int port) {
        return new ModbusClient(host, port);
    }
    
    /**
     * Initialisiert den Service und die Modbus-Verbindung.
     */
    public void initialize() {
        modbusClient.initialize();
    }
    
    /**
     * Beendet den Service sauber.
     */
    public void shutdown() {
        modbusClient.shutdown();
    }
    
    /**
     * Liest die aktuelle Photovoltaik-Leistung (Stromproduktion).
     * 
     * @return Aktuelle PV-Leistung in Watt
     * @throws ExecutionException wenn ein Fehler beim Lesen auftritt
     * @throws InterruptedException wenn die Operation unterbrochen wird
     */
    private int getCurrentPowerProduction() {
        // PV_POWER ist ein 32-bit Integer über 2 Register
        byte[] registers = modbusClient.readRegistersSync(BatteryModbusRegisters.PV_POWER, 2);
        return (registers[0] << 16) | registers[1];
    }
    
    /**
     * Liest den aktuellen Hausverbrauch (direkter Verbrauch).
     * 
     * @return Aktueller Hausverbrauch in Watt
     * @throws ExecutionException wenn ein Fehler beim Lesen auftritt
     * @throws InterruptedException wenn die Operation unterbrochen wird
     */
    private int getCurrentPowerConsumption() {
        // HOME_POWER ist ein 32-bit Integer über 2 Register
        byte[] registers = modbusClient.readRegistersSync(BatteryModbusRegisters.HOME_POWER, 2);
        return (registers[0] << 16) | registers[1];
    }
    
    /**
     * Liest den aktuellen Ladezustand des Batteriespeichers.
     * 
     * @return Ladezustand in Prozent (0-100)
     * @throws ExecutionException wenn ein Fehler beim Lesen auftritt
     * @throws InterruptedException wenn die Operation unterbrochen wird
     */
    private int getBatteryStateOfCharge() {
        // BATTERY_SOC ist ein UInt16 in einem Register
        byte[] registers = modbusClient.readRegistersSync(BatteryModbusRegisters.BATTERY_SOC, 1);
        return registers[0];
    }
    
    /**
     * Sammelt alle wichtigen Leistungsdaten in einem Objekt.
     * 
     * @return PowerStatus Objekt mit allen aktuellen Werten
     * @throws ExecutionException wenn ein Fehler beim Lesen auftritt
     * @throws InterruptedException wenn die Operation unterbrochen wird
     */
    public PowerStatus getCurrentPowerStatus() throws Exception {
        return PowerStatus.builder()
                .powerProduction(getCurrentPowerProduction())
                .powerConsumption(getCurrentPowerConsumption())
                .batteryStateOfCharge(getBatteryStateOfCharge())
                .build();
    }
}