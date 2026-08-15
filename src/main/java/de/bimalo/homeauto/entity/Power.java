package de.bimalo.homeauto.entity;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a power value with sign.
 *
 * Positive: Consumption/Charging
 * Negative: Generation/Discharging
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Power {

    public static final Power ZERO = new Power(0);

    private final long watts;

    public static Power ofWatts(long watts) {
        return new Power(watts);
    }

    public static Power ofWatts(int watts) {
        return new Power(watts); // Converts int to long
    }

    public static Power ofKilowatts(double kilowatts) {
        return new Power((long) (kilowatts * 1000));
    }

    public double getKilowatts() {
        return watts / 1000.0;
    }

    public boolean isPositive() {
        return watts > 0;
    }

    public boolean isNegative() {
        return watts < 0;
    }

    public boolean isGreaterThan(Power other) {
        return this.watts > other.watts;
    }

    public boolean isGreaterThan(long watts) {
        return this.watts > watts;
    }

    public boolean isLessThan(Power other) {
        return this.watts < other.watts;
    }

    public boolean isLessThan(long watts) {
        return this.watts < watts;
    }

    public Power negate() {
        return new Power(-watts);
    }

    public Power increase(Power other) {
        return new Power(this.watts + other.watts);
    }

    public Power reduce(Power other) {
        return new Power(this.watts - other.watts);
    }

    public Power reduce(long other) {
        return new Power(this.watts - other);
    }

    public static Power max(Power a, Power b) {
        return a.watts >= b.watts ? a : b;
    }

    public Power atLeast(Power minimum) {
        return max(this, minimum);
    }

    /**
     * Formats for UI display
     */
    public String format() {
        if (Math.abs(watts) >= 1000) {
            return String.format("%.2f kWh", getKilowatts());
        }
        return watts + " W";
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
        Power power = (Power) o;
        return watts == power.watts;
    }

    @Override
    public int hashCode() {
        return Objects.hash(watts);
    }
}
