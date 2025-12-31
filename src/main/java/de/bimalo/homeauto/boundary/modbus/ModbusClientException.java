package de.bimalo.homeauto.boundary.modbus;

import lombok.Getter;

/**
 * Base exception for Modbus client communication errors.
 * Contains context information about the connection and operation that failed.
 */
@Getter
public class ModbusClientException extends RuntimeException {

    private final String host;
    private final int port;
    private final Integer registerAddress;

    public ModbusClientException(String message, String host, int port) {
        super(formatMessage(message, host, port, null));
        this.host = host;
        this.port = port;
        this.registerAddress = null;
    }

    public ModbusClientException(String message, String host, int port, Throwable cause) {
        super(formatMessage(message, host, port, null), cause);
        this.host = host;
        this.port = port;
        this.registerAddress = null;
    }

    public ModbusClientException(String message, String host, int port, int registerAddress, Throwable cause) {
        super(formatMessage(message, host, port, registerAddress), cause);
        this.host = host;
        this.port = port;
        this.registerAddress = registerAddress;
    }

    private static String formatMessage(String message, String host, int port, Integer registerAddress) {
        if (registerAddress != null) {
            return String.format("%s (Host: %s:%d, Register: %d)", message, host, port, registerAddress);
        }
        return String.format("%s (Host: %s:%d)", message, host, port);
    }
}
