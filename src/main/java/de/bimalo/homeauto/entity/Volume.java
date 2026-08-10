package de.bimalo.homeauto.entity;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a volume value in cubic meters.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Volume {

    public static final Volume ZERO = new Volume(0);

    private final double cubicMeters;

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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Volume volume = (Volume) o;
        return Double.compare(volume.cubicMeters, cubicMeters) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cubicMeters);
    }
}
