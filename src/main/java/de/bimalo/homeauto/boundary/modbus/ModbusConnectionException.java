package de.bimalo.homeauto.boundary.modbus;

/**
 * Exception thrown when a Modbus connection cannot be established or is lost.
 * This typically indicates network issues or that the device is offline.
 */
public class ModbusConnectionException extends ModbusClientException {

    public ModbusConnectionException(String message, String host, int port, Throwable cause) {
        super(message, host, port, cause);
    }
}
