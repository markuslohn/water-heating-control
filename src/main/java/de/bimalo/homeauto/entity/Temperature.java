package de.bimalo.homeauto.entity;

/**
 * Represents a temperature value.
 */
public record Temperature(double celsius) {

    public static Temperature ofCelsius(double celsius) {
        return new Temperature(celsius);
    }

    public static Temperature ofCelsius(int celsius) {
        return new Temperature(Integer.valueOf(celsius).doubleValue());
    }

    /**
     * Returns true if temperature is too hot (above 45°C)
     * Used for battery temperature monitoring
     */
    public boolean isHot() {
        return celsius > 45;
    }

    public boolean isCold() {
        return celsius < 0;
    }

    public String format() {
        return String.format("%.1f°C", celsius);
    }

    @Override
    public String toString() {
        return format();
    }
}
