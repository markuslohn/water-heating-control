package de.bimalo.homeauto.control.heatingcontrol;

import de.bimalo.homeauto.entity.Temperature;

/**
 * Result of a temperature check containing current and target temperatures.
 */
public record TemperatureCheck(Temperature current, Temperature target) {

    /**
     * Checks if the target temperature has been reached.
     *
     * @return true if current temperature is greater than or equal to target temperature
     */
    public boolean targetReached() {
        return current.getCelsius() >= target.getCelsius();
    }

    /**
     * Gets the current temperature in Celsius.
     *
     * @return current temperature in degrees Celsius
     */
    public double currentCelsius() {
        return current.getCelsius();
    }

    /**
     * Gets the target temperature in Celsius.
     *
     * @return target temperature in degrees Celsius
     */
    public double targetCelsius() {
        return target.getCelsius();
    }
}
