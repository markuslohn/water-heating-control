package de.bimalo.homeauto.boundary.modbus;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

/**
 * Generische Modbus TCP Client Implementierung.
 * Diese Klasse stellt die grundlegende Kommunikation über Modbus TCP bereit.
 */
@Slf4j
public class ModbusClient {

    private ModbusTcpClient client;
    private final String host;
    private final int port;

    /**
     * Konstruktor für den Modbus-Client.
     *
     * @param host Die IP-Adresse oder Hostname des Modbus-Servers
     * @param port Der TCP-Port des Modbus-Servers
     */
    public ModbusClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Initialisiert die Modbus-Verbindung.
     */
    public void initialize() {
        log.info("Initialisiere Modbus-Client für {}:{}", host, port);

        var transport = NettyTcpClientTransport.create(cfg -> {
            cfg.setHostname(host);
            cfg.setPort(port);
        });

        client = ModbusTcpClient.create(transport);
        try {
            client.connect();
        } catch (ModbusExecutionException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Beendet die Modbus-Verbindung sauber.
     */
    public void shutdown() {
        if (client != null) {
            log.info("Beende Modbus-Client für {}:{}", host, port);
            try {
                client.disconnect();
            } catch (ModbusExecutionException e) {
                log.error(String.format("Error when disconnecting modbus client {}:{}", host, port), e);
            }
        }
    }

    /**
     * Liest Modbus-Register synchron aus.
     *
     * @param address  Die Startadresse der Register
     * @param quantity Die Anzahl der zu lesenden Register
     * @return Array mit den gelesenen Registerwerten
     * @throws ExecutionException       wenn ein Fehler beim Lesen auftritt
     * @throws InterruptedException     wenn die Operation unterbrochen wird
     * @throws ModbusTimeoutException
     * @throws ModbusResponseException
     * @throws ModbusExecutionException
     */
    public byte[] readRegistersSync(int address, int quantity) throws RuntimeException {
        try {
            ReadHoldingRegistersResponse response = client.readHoldingRegisters(1,
                    new ReadHoldingRegistersRequest(address, quantity));
            return response.registers();
        } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
