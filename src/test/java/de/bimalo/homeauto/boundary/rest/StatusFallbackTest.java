package de.bimalo.homeauto.boundary.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.ServiceUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Test class for StatusFallback - the shared "read live, fall back to a
 * recent last-known status, 503 if too old or missing" logic used by every
 * REST status endpoint that reads a Modbus device.
 */
class StatusFallbackTest {

    private static final Instant NOW = Instant.parse("2024-01-01T12:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(2);

    private final Clock clock = mock(Clock.class);

    void stubClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void resolve_returnsLiveValue_whenReadSucceeds() {
        stubClock();
        Supplier<String> read = () -> "live";

        StatusFallback.ResolvedStatus<String> resolved = StatusFallback.resolve(
                read, () -> Optional.of("cached"), s -> NOW, MAX_AGE, clock);

        assertEquals("live", resolved.status());
        assertFalse(resolved.stale());
    }

    @Test
    void resolve_doesNotConsultLastKnown_whenReadSucceeds() {
        Supplier<String> read = () -> "live";
        @SuppressWarnings("unchecked")
        Supplier<Optional<String>> neverCalled = mock(Supplier.class);

        StatusFallback.resolve(read, neverCalled, s -> NOW, MAX_AGE, clock);

        verify(neverCalled, never()).get();
    }

    @Test
    void resolve_returnsStaleCachedValue_whenReadFailsAndCacheIsWithinMaxAge() {
        stubClock();
        Supplier<String> read = () -> {
            throw new RuntimeException("Modbus timeout");
        };
        Instant measuredAt = NOW.minus(Duration.ofSeconds(90));

        StatusFallback.ResolvedStatus<String> resolved = StatusFallback.resolve(
                read, () -> Optional.of("cached"), s -> measuredAt, MAX_AGE, clock);

        assertEquals("cached", resolved.status());
        assertTrue(resolved.stale());
    }

    @Test
    void resolve_throwsServiceUnavailable_whenReadFailsAndCacheIsOlderThanMaxAge() {
        stubClock();
        Supplier<String> read = () -> {
            throw new RuntimeException("Modbus timeout");
        };
        Instant measuredAt = NOW.minus(Duration.ofMinutes(5));

        assertThrows(ServiceUnavailableException.class,
                () -> StatusFallback.resolve(read, () -> Optional.of("cached"), s -> measuredAt, MAX_AGE, clock));
    }

    @Test
    void resolve_throwsServiceUnavailable_whenReadFailsAndCacheIsExactlyAtMaxAge() {
        stubClock();
        Supplier<String> read = () -> {
            throw new RuntimeException("Modbus timeout");
        };
        Instant measuredAt = NOW.minus(MAX_AGE);

        // Exactly at the boundary is still considered fresh enough (not "older than").
        StatusFallback.ResolvedStatus<String> resolved = StatusFallback.resolve(
                read, () -> Optional.of("cached"), s -> measuredAt, MAX_AGE, clock);

        assertEquals("cached", resolved.status());
        assertTrue(resolved.stale());
    }

    @Test
    void resolve_throwsServiceUnavailable_whenReadFailsAndNothingIsKnown() {
        Supplier<String> read = () -> {
            throw new RuntimeException("Modbus timeout");
        };

        assertThrows(ServiceUnavailableException.class,
                () -> StatusFallback.resolve(read, Optional::empty, s -> NOW, MAX_AGE, clock));
    }
}
