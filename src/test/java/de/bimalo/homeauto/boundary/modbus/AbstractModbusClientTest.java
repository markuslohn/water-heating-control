package de.bimalo.homeauto.boundary.modbus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import de.bimalo.homeauto.entity.DeviceInfo;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

/**
 * Test class for AbstractModbusClient.
 * Verifies the startup-resilience behavior (K1): a device that is unreachable
 * must not prevent {@link AbstractModbusClient#initialize()} from returning,
 * and a subsequent device operation must transparently retry the connection
 * rather than staying broken forever. Uses a closed local TCP port instead of
 * a real Modbus device, so these tests only exercise the TCP-level connect
 * failure path, not actual Modbus protocol traffic.
 * Also verifies that a failed read/write disconnects a half-open connection
 * (Bug 3) using a mocked {@link ModbusTcpClient}, since neither it nor its
 * relevant methods are final.
 */
class AbstractModbusClientTest {

    @Test
    void initialize_doesNotThrow_whenDeviceIsUnreachable() throws IOException {
        try (TestModbusClient client = new TestModbusClient("127.0.0.1", closedPort())) {
            assertDoesNotThrow(client::initialize);
            assertFalse(client.isConnected());
        }
    }

    @Test
    void readOperation_attemptsReconnectAndThrowsConnectionException_whenDeviceStaysUnreachable() throws IOException {
        try (TestModbusClient client = new TestModbusClient("127.0.0.1", closedPort())) {
            client.initialize();

            assertThrows(ModbusConnectionException.class, client::probeRead);
            assertFalse(client.isConnected());
        }
    }

    @Test
    void readOperation_disconnectsClient_whenReadFails() throws Exception {
        ModbusTcpClient modbusTcpClient = mock(ModbusTcpClient.class);
        when(modbusTcpClient.isConnected()).thenReturn(true);
        when(modbusTcpClient.readHoldingRegisters(anyInt(), any(ReadHoldingRegistersRequest.class)))
                .thenThrow(new ModbusTimeoutException("timed out"));
        TestModbusClient client = new TestModbusClient("127.0.0.1", 502, modbusTcpClient);

        assertThrows(ModbusReadException.class, client::probeRead);

        verify(modbusTcpClient).disconnect();
    }

    @Test
    void readOperation_reconnects_afterPreviousFailureInvalidatedConnection() throws Exception {
        ModbusTcpClient modbusTcpClient = mock(ModbusTcpClient.class);
        // First call: connection looks up, but the read itself fails and the
        // failure disconnects it. Second call: checkConnectivity() now sees a
        // disconnected client, reconnects, and the read succeeds.
        when(modbusTcpClient.isConnected()).thenReturn(true, false, true);
        when(modbusTcpClient.readHoldingRegisters(anyInt(), any(ReadHoldingRegistersRequest.class)))
                .thenThrow(new ModbusTimeoutException("timed out"))
                .thenReturn(new ReadHoldingRegistersResponse(new byte[] {0, 5}));
        TestModbusClient client = new TestModbusClient("127.0.0.1", 502, modbusTcpClient);

        assertThrows(ModbusReadException.class, client::probeRead);
        int value = client.probeRead();

        assertEquals(5, value);
        verify(modbusTcpClient).disconnect();
        verify(modbusTcpClient).connect();
    }

    /**
     * Binds a server socket to get a free local port, then immediately closes
     * it again so nothing is listening there any more - connecting to it fails
     * fast (connection refused) instead of waiting for a connect timeout.
     */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class TestModbusClient extends AbstractModbusClient {

        TestModbusClient(String host, int port) {
            super(host, port);
        }

        TestModbusClient(String host, int port, ModbusTcpClient client) {
            super(host, port, client);
        }

        @Override
        protected DeviceInfo readDeviceInfo() {
            return DeviceInfo.builder()
                    .manufacturer("test")
                    .model("test")
                    .serialNumber("1")
                    .firmwareVersion("1")
                    .build();
        }

        int probeRead() {
            return readUnsignedInteger(0);
        }
    }
}
