package de.bimalo.homeauto.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Month;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeasonTest {

    @ParameterizedTest
    @CsvSource({
            "1, WINTER",
            "2, WINTER",
            "3, SPRING",
            "4, SPRING",
            "5, SUMMER",
            "6, SUMMER",
            "7, SUMMER",
            "8, SUMMER",
            "9, AUTUMN",
            "10, AUTUMN",
            "11, WINTER",
            "12, WINTER"
    })
    void testFromMonth_ShouldReturnCorrectSeason(int month, Season expectedSeason) {
        assertEquals(expectedSeason, Season.fromMonth(month));
    }

    @Test
    void testFromMonth_WithInvalidMonth_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> Season.fromMonth(0));
        assertThrows(IllegalArgumentException.class, () -> Season.fromMonth(13));
    }

    @Test
    void testFromMonthEnum_ShouldReturnCorrectSeason() {
        assertEquals(Season.WINTER, Season.fromMonth(Month.JANUARY));
        assertEquals(Season.WINTER, Season.fromMonth(Month.FEBRUARY));
        assertEquals(Season.SPRING, Season.fromMonth(Month.MARCH));
        assertEquals(Season.SPRING, Season.fromMonth(Month.APRIL));
        assertEquals(Season.SUMMER, Season.fromMonth(Month.MAY));
        assertEquals(Season.SUMMER, Season.fromMonth(Month.JUNE));
        assertEquals(Season.SUMMER, Season.fromMonth(Month.JULY));
        assertEquals(Season.SUMMER, Season.fromMonth(Month.AUGUST));
        assertEquals(Season.AUTUMN, Season.fromMonth(Month.SEPTEMBER));
        assertEquals(Season.AUTUMN, Season.fromMonth(Month.OCTOBER));
        assertEquals(Season.WINTER, Season.fromMonth(Month.NOVEMBER));
        assertEquals(Season.WINTER, Season.fromMonth(Month.DECEMBER));
    }

    @Test
    void testGetDisplayName() {
        assertEquals("Winter", Season.WINTER.getDisplayName());
        assertEquals("Frühling", Season.SPRING.getDisplayName());
        assertEquals("Sommer", Season.SUMMER.getDisplayName());
        assertEquals("Herbst", Season.AUTUMN.getDisplayName());
    }

    @Test
    void testGetEmoji() {
        assertEquals("❄️", Season.WINTER.getEmoji());
        assertEquals("🌱", Season.SPRING.getEmoji());
        assertEquals("☀️", Season.SUMMER.getEmoji());
        assertEquals("🍂", Season.AUTUMN.getEmoji());
    }

    @Test
    void testToDisplayString() {
        assertEquals("❄️ Winter", Season.WINTER.toDisplayString());
        assertEquals("🌱 Frühling", Season.SPRING.toDisplayString());
        assertEquals("☀️ Sommer", Season.SUMMER.toDisplayString());
        assertEquals("🍂 Herbst", Season.AUTUMN.toDisplayString());
    }

    @Test
    void testCurrent_ShouldNotBeNull() {
        // Current season depends on the actual date, so we just verify it returns a valid season
        Season current = Season.current();
        assertEquals(current, Season.fromMonth(java.time.LocalDate.now().getMonthValue()));
    }
}
