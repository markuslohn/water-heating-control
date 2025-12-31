package de.bimalo.homeauto.entity;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a temperature value.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Temperature {
    private final double celsius;

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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Temperature that = (Temperature) o;
        return Double.compare(that.celsius, celsius) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(celsius);
    }
}
