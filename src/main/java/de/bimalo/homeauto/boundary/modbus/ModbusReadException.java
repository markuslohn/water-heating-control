package de.bimalo.homeauto.boundary.modbus;

/**
 * Exception thrown when reading from a Modbus register fails.
 * This can indicate timeouts, invalid register addresses, or communication errors.
 */
public class ModbusReadException extends ModbusClientException {

    public ModbusReadException(String message, String host, int port, int registerAddress, Throwable cause) {
        super(message, host, port, registerAddress, cause);
    }
}
