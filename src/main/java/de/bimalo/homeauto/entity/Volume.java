package de.bimalo.homeauto.entity;

/**
 * Represents a volume value in cubic meters.
 */
public record Volume(double cubicMeters) {

    public static final Volume ZERO = new Volume(0);

    public static Volume ofCubicMeters(double cubicMeters) {
        return new Volume(cubicMeters);
    }

    public String format() {
        return String.format("%.1f m³", cubicMeters);
    }

    @Override
    public String toString() {
        return format();
    }
}
