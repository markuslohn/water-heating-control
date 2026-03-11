package de.bimalo.homeauto.entity;

import java.time.LocalDate;
import java.time.Month;
import lombok.Getter;

/**
 * Represents the seasons used for heating control scheduling.
 * Each season has specific months and operating hours defined in the configuration.
 */
@Getter
public enum Season {
    WINTER("Winter", "\u2744\uFE0F"),      // ❄️
    SPRING("Frühling", "\uD83C\uDF31"),    // 🌱
    SUMMER("Sommer", "\u2600\uFE0F"),      // ☀️
    AUTUMN("Herbst", "\uD83C\uDF42");      // 🍂

    private final String displayName;
    private final String emoji;

    Season(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    /**
     * Determines the current season based on the current date.
     *
     * @return the current season
     */
    public static Season current() {
        return fromMonth(LocalDate.now().getMonthValue());
    }

    /**
     * Determines the season for a given month.
     * Based on the cron expressions in HeatingControlConfig:
     * - Winter: Nov-Feb (11, 12, 1, 2)
     * - Spring: Mar-Apr (3, 4)
     * - Summer: May-Aug (5, 6, 7, 8)
     * - Autumn: Sep-Oct (9, 10)
     *
     * @param month the month (1-12)
     * @return the season for the given month
     */
    public static Season fromMonth(int month) {
        return switch (month) {
            case 11, 12, 1, 2 -> WINTER;
            case 3, 4 -> SPRING;
            case 5, 6, 7, 8 -> SUMMER;
            case 9, 10 -> AUTUMN;
            default -> throw new IllegalArgumentException("Invalid month: " + month);
        };
    }

    /**
     * Determines the season for a given Month enum.
     *
     * @param month the Month enum value
     * @return the season for the given month
     */
    public static Season fromMonth(Month month) {
        return fromMonth(month.getValue());
    }

    /**
     * Returns the display string with emoji and name.
     *
     * @return formatted string like "☀️ Sommer"
     */
    public String toDisplayString() {
        return emoji + " " + displayName;
    }
}
