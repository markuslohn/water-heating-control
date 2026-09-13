package de.bimalo.homeauto.entity;

/**
 * Represents a percentage value (0-100)
 */
public record Percentage(int value) {

    public Percentage {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(
                    "Percentage must be between 0 and 100, got: " + value);
        }
    }

    public static Percentage of(int value) {
        return new Percentage(value);
    }

    public static Percentage ofDecimal(double decimal) {
        return of((int) (decimal * 100));
    }

    public double asDecimal() {
        return value / 100.0;
    }

    public boolean isLow() {
        return value < 20;
    }

    public boolean isHigh() {
        return value > 80;
    }

    public boolean isCritical() {
        return value < 10;
    }

    public boolean isLessThan(Percentage other) {
        return this.value < other.value;
    }

    public boolean isLessThan(int other) {
        return this.isLessThan(Percentage.of(other));
    }

    public String format() {
        return value + "%";
    }

    /**
     * Returns a visual bar representation
     */
    public String toBar(int length) {
        int filled = (int) (length * (value / 100.0));
        return "█".repeat(filled) + "░".repeat(length - filled) + " " + value + "%";
    }

    @Override
    public String toString() {
        return format();
    }
}
