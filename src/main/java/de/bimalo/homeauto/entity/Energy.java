package de.bimalo.homeauto.entity;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents an energy value in watt hours with sign
 *
 * Positive: Consumed energy
 * Negative: Generated energy
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Energy {

    private final long wattHours;

    public static Energy ofWattHours(long wattHours) {
        return new Energy(wattHours);
    }

    public static Energy ofKilowattHours(double kilowattHours) {
        return new Energy((long) (kilowattHours * 1000));
    }

    public static Energy fromPowerAndHours(Power power, double hours) {
        return new Energy((long) (power.getWatts() * hours));
    }

    public double getKilowattHours() {
        return wattHours / 1000.0;
    }

    public double getMegawattHours() {
        return wattHours / 1_000_000.0;
    }

    public boolean isPositive() {
        return wattHours > 0;
    }

    public boolean isNegative() {
        return wattHours < 0;
    }

    public Energy negate() {
        return new Energy(-wattHours);
    }

    public Energy add(Energy other) {
        return new Energy(this.wattHours + other.wattHours);
    }

    public Energy subtract(Energy other) {
        return new Energy(this.wattHours - other.wattHours);
    }

    /**
     * Formats for UI display
     */
    public String format() {
        if (wattHours >= 1_000_000) {
            return String.format("%.2f MWh", getMegawattHours());
        } else if (wattHours >= 1000) {
            return String.format("%.2f kWh", getKilowattHours());
        }
        return wattHours + " Wh";
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
        Energy energy = (Energy) o;
        return wattHours == energy.wattHours;
    }

    @Override
    public int hashCode() {
        return Objects.hash(wattHours);
    }
}
