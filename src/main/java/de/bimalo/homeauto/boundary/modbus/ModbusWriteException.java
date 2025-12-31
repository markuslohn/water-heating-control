package de.bimalo.homeauto.boundary.modbus;

/**
 * Exception thrown when writing to a Modbus register fails.
 * This can indicate timeouts, invalid register addresses, or communication errors.
 */
public class ModbusWriteException extends ModbusClientException {

    public ModbusWriteException(String message, String host, int port, int registerAddress, Throwable cause) {
        super(message, host, port, registerAddress, cause);
    }
}
