package de.bimalo.homeauto.boundary.modbus;

import com.digitalpetri.modbus.client.ModbusTcpClient;
import com.digitalpetri.modbus.client.ModbusTcpClientTransport;
import com.digitalpetri.modbus.exceptions.ModbusExecutionException;
import com.digitalpetri.modbus.exceptions.ModbusResponseException;
import com.digitalpetri.modbus.exceptions.ModbusTimeoutException;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.pdu.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.pdu.ReadInputRegistersRequest;
import com.digitalpetri.modbus.pdu.ReadInputRegistersResponse;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterResponse;
import com.digitalpetri.modbus.tcp.client.NettyTcpClientTransport;
import de.bimalo.homeauto.entity.DeviceInfo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public abstract class AbstractModbusClient implements AutoCloseable {

    private static final int TIMEOUT_SECONDS = 5;

    // Guards all access to the shared ModbusTcpClient. Read methods used to run
    // unsynchronized alongside synchronized write/keep-alive methods in the
    // subclass adapters, so a scheduled read and a scheduled write could reach
    // the same TCP connection concurrently; this lock closes that gap centrally
    // instead of relying on every subclass to synchronize consistently.
    private final Object modbusLock = new Object();

    private ModbusTcpClient client;
    private final String host;
    private final int port;

    protected AbstractModbusClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    AbstractModbusClient(String host, int port, ModbusTcpClient client) {
        this.host = host;
        this.port = port;
        this.client = client;
    }

    /**
     * Prepares the Modbus client and attempts an initial connection. A failed
     * initial connection does not prevent startup: it is logged, and the actual
     * connection is retried on demand by {@link #checkConnectivity()} the next
     * time a device operation is attempted (e.g. by a control cycle's scheduled
     * read/write), rather than only once at startup.
     */
    public void initialize() {
        synchronized (modbusLock) {
            log.info("Connect modbus client to {}:{}...", host, port);

            ModbusTcpClientTransport config = NettyTcpClientTransport.create(
                    cfg -> {
                        cfg.setHostname(host);
                        cfg.setPort(port);
                        cfg.setConnectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
                    });

            client = ModbusTcpClient.create(config);
            connect();
        }
    }

    /**
     * Attempts to (re)connect the already-created client. Must be called while
     * holding {@code modbusLock}.
     */
    private void connect() {
        if (client == null) {
            // initialize() was never called; nothing to (re)connect yet.
            return;
        }
        try {
            client.connect();
            log.info("Connected modbus client to {}:{}", host, port);
            logDeviceInfo();
        } catch (ModbusExecutionException e) {
            log.error("Modbus device at {}:{} not reachable; will retry on next access", host, port, e);
        }
    }

    private void logDeviceInfo() {
        try {
            DeviceInfo info = readDeviceInfo();
            log.info("Device at {}:{} is {} from {} (Serial: {})",
                    host, port, info.getModel(), info.getManufacturer(), info.getSerialNumber());
        } catch (RuntimeException e) {
            log.warn("Connected to {}:{} but could not read device info", host, port, e);
        }
    }

    public boolean isConnected() {
        synchronized (modbusLock) {
            return client != null && client.isConnected();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Disconnects the modbus connection.
     */
    public void shutdown() {
        synchronized (modbusLock) {
            if (client != null) {
                log.info("Disconnect modbus client for {}:{}", host, port);
                try {
                    client.disconnect();
                } catch (ModbusExecutionException e) {
                    log.error(String.format("Error when disconnecting modbus client {}:{}", host, port), e);
                }
            }
        }
    }

    protected String readString(int address, int quantity) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads string value from modbus register address {}...", address);
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(address, quantity);
            try {
                ReadHoldingRegistersResponse response = client.readHoldingRegisters(1, request);
                ByteBuffer registers = ByteBuffer.wrap(response.registers());
                return StandardCharsets.ISO_8859_1.decode(registers).toString();
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read string from register", host, port, address, e);
            }
        }
    }

    protected int readInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads int value from modbus register address {}...", address);
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(address, 1);
            try {
                ReadHoldingRegistersResponse response = client.readHoldingRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(ByteOrder.BIG_ENDIAN);
                return buffer.getShort();
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read integer from register", host, port, address, e);
            }
        }
    }

    protected int readUnsignedInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads unsigned int value from modbus register address {}...", address);
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(address, 1);
            try {
                ReadHoldingRegistersResponse response = client.readHoldingRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(ByteOrder.BIG_ENDIAN);
                return buffer.getShort() & 0xFFFF;
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read unsigned integer from register", host, port, address, e);
            }
        }
    }

    protected long read32BitInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads 32bit int value from modbus register address {}...", address);
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(address, 2);
            try {
                ReadHoldingRegistersResponse response = client.readHoldingRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(getByteOrder());

                int word0 = buffer.getShort(0) & 0xFFFF; // Bytes 0-1
                int word1 = buffer.getShort(2) & 0xFFFF; // Bytes 2-3

                return compose32BitValue(word0, word1);
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read 32-bit integer from register", host, port, address, e);
            }
        }
    }

    /**
     * Reads an unsigned 32-bit integer value from two consecutive holding
     * registers.
     * Always interprets the value as unsigned (0 to 4,294,967,295).
     *
     * @param address the starting register address
     * @return the unsigned 32-bit value as a long
     * @throws ModbusReadException if reading fails
     */
    protected long readUnsigned32BitInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads unsigned 32bit int value from modbus register address {}...", address);
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(address, 2);
            try {
                ReadHoldingRegistersResponse response = client.readHoldingRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(getByteOrder());

                int word0 = buffer.getShort(0) & 0xFFFF; // Bytes 0-1
                int word1 = buffer.getShort(2) & 0xFFFF; // Bytes 2-3

                return composeUnsigned32BitValue(word0, word1);
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read unsigned 32-bit integer from register", host, port,
                        address, e);
            }
        }
    }

    /**
     * Composes a 32-bit value from two 16-bit words.
     * Can be overridden by subclasses to handle different word orders or sign
     * interpretations.
     *
     * @param word0 first word (bytes 0-1)
     * @param word1 second word (bytes 2-3)
     * @return the composed 32-bit value
     */
    protected long compose32BitValue(int word0, int word1) {
        int highWord = word0;
        int lowWord = word1;

        return ((long) highWord << 16) | lowWord;
    }

    /**
     * Composes an unsigned 32-bit value from two 16-bit words.
     * Can be overridden by subclasses to handle different word orders.
     *
     * @param word0 first word (bytes 0-1)
     * @param word1 second word (bytes 2-3)
     * @return the composed unsigned 32-bit value
     */
    protected long composeUnsigned32BitValue(int word0, int word1) {
        int highWord = word0;
        int lowWord = word1;

        return ((long) highWord << 16) | lowWord;
    }

    protected ByteOrder getByteOrder() {
        return ByteOrder.BIG_ENDIAN; // Default
    }

    protected String readInputString(int address, int quantity) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads string value from input register address {}...", address);
            ReadInputRegistersRequest request = new ReadInputRegistersRequest(address, quantity);
            try {
                ReadInputRegistersResponse response = client.readInputRegisters(1, request);
                ByteBuffer registers = ByteBuffer.wrap(response.registers());
                return StandardCharsets.ISO_8859_1.decode(registers).toString();
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read string from input register", host, port, address, e);
            }
        }
    }

    protected int readInputInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads int value from input register address {}...", address);
            ReadInputRegistersRequest request = new ReadInputRegistersRequest(address, 1);
            try {
                ReadInputRegistersResponse response = client.readInputRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(ByteOrder.BIG_ENDIAN);
                return buffer.getShort();
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read integer from input register", host, port, address, e);
            }
        }
    }

    protected int readInputUnsignedInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads unsigned int value from input register address {}...", address);
            ReadInputRegistersRequest request = new ReadInputRegistersRequest(address, 1);
            try {
                ReadInputRegistersResponse response = client.readInputRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(ByteOrder.BIG_ENDIAN);
                return buffer.getShort() & 0xFFFF;
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read unsigned integer from input register", host, port,
                        address, e);
            }
        }
    }

    protected long readInput32BitInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads 32bit int value from input register address {}...", address);
            ReadInputRegistersRequest request = new ReadInputRegistersRequest(address, 2);
            try {
                ReadInputRegistersResponse response = client.readInputRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(getByteOrder());

                int word0 = buffer.getShort(0) & 0xFFFF; // Bytes 0-1
                int word1 = buffer.getShort(2) & 0xFFFF; // Bytes 2-3

                return compose32BitValue(word0, word1);

            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read 32-bit integer from input register", host, port,
                        address, e);
            }
        }
    }

    /**
     * Reads an unsigned 32-bit integer value from two consecutive input registers.
     * Always interprets the value as unsigned (0 to 4,294,967,295).
     *
     * @param address the starting register address
     * @return the unsigned 32-bit value as a long
     * @throws ModbusReadException if reading fails
     */
    protected long readInputUnsigned32BitInteger(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads unsigned 32bit int value from input register address {}...", address);
            ReadInputRegistersRequest request = new ReadInputRegistersRequest(address, 2);
            try {
                ReadInputRegistersResponse response = client.readInputRegisters(1, request);
                ByteBuffer buffer = ByteBuffer.wrap(response.registers());
                buffer.order(getByteOrder());

                int word0 = buffer.getShort(0) & 0xFFFF; // Bytes 0-1
                int word1 = buffer.getShort(2) & 0xFFFF; // Bytes 2-3

                return composeUnsigned32BitValue(word0, word1);

            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read unsigned 32-bit integer from input register", host,
                        port, address, e);
            }
        }
    }

    /**
     * Reads a status value from a discrete input register.
     *
     * @param address the discrete input address
     * @return the status as an integer (0 or 1)
     * @throws ModbusReadException if reading fails
     */
    protected int readDiscreteInputStatus(int address) {
        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Reads discrete input status from modbus register address {}...", address);
            ReadDiscreteInputsRequest request = new ReadDiscreteInputsRequest(address, 1);
            try {
                ReadDiscreteInputsResponse response = client.readDiscreteInputs(1, request);
                return response.inputs()[0] & 0x01;
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusReadException("Failed to read discrete input status", host, port, address, e);
            }
        }
    }

    /**
     * Writes an unsigned 16-bit integer value to a holding register.
     *
     * @param address the register address to write to
     * @param value   the unsigned integer value (0-65535) to write
     * @throws ModbusWriteException     if writing fails
     * @throws IllegalArgumentException if value is out of range
     */
    protected void writeUnsignedInteger(int address, int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(
                    String.format("Value %d is out of range for unsigned 16-bit integer (0-65535)", value));
        }

        synchronized (modbusLock) {
            checkConnectivity();

            log.debug("Writes unsigned int value {} to modbus register address {}...", value, address);

            WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(address, value);
            try {
                WriteSingleRegisterResponse response = client.writeSingleRegister(1, request);
                log.debug("Successfully wrote value {} to register address {}", value, address);
            } catch (ModbusExecutionException | ModbusResponseException | ModbusTimeoutException e) {
                disconnectAfterFailure(e);
                throw new ModbusWriteException("Failed to write unsigned integer to register", host, port, address,
                        e);
            }
        }
    }

    /**
     * Reads device specific information from modbus registers
     */
    protected abstract DeviceInfo readDeviceInfo();

    protected double scaleReadValue(int rawValue, double factor) {
        return rawValue * factor;
    }

    protected int scaleReadEnumValue(int rawValue, double factor) {
        double scaledValue = rawValue * factor;
        long roundedValue = Math.round(scaledValue);

        if (Math.abs(scaledValue - roundedValue) > 0.000_001) {
            throw new IllegalArgumentException("Scaled enum value is not an integer: " + scaledValue);
        }

        return Math.toIntExact(roundedValue);
    }

    protected int scaleWriteValue(double value, double factor) {
        if (!Double.isFinite(value) || !Double.isFinite(factor)) {
            throw new IllegalArgumentException("Value and scale factor must be finite");
        }

        return Math.toIntExact(Math.round(value * factor));
    }

    /**
     * Must be called while holding {@code modbusLock}. Opportunistically retries
     * the connection if it isn't up yet, so a device that was unreachable at
     * startup (or dropped its connection later) reconnects on the next access
     * instead of staying broken until the application is restarted.
     */
    private void checkConnectivity() {
        if (!isConnected()) {
            connect();
            if (!isConnected()) {
                throw new ModbusConnectionException("Modbus client is not connected", host, port, null);
            }
        }
    }

    /**
     * Closes the connection after a failed read/write so a half-open TCP
     * connection (where {@code isConnected()} would otherwise keep reporting
     * true) is not reused by the next call. {@link #checkConnectivity()}
     * reconnects on the next access.
     */
    private void disconnectAfterFailure(Exception cause) {
        try {
            client.disconnect();
        } catch (ModbusExecutionException ex) {
            cause.addSuppressed(ex);
        }
    }
}
