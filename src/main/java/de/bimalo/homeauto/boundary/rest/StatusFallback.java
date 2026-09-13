package de.bimalo.homeauto.boundary.rest;

import jakarta.ws.rs.ServiceUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared "read live, fall back to last known status on failure" logic for
 * the REST status endpoints. A fallback is only served while it is no older
 * than {@code maxAge}; beyond that, callers get a 503 instead of silently
 * stale data.
 */
final class StatusFallback {

    private StatusFallback() {
    }

    /**
     * @param read       performs the live device read
     * @param lastKnown  returns the most recently successful read, if any
     * @param measuredAt extracts the timestamp a status was measured at
     * @param maxAge     maximum age of a last-known status still usable as fallback
     * @param clock      clock used to evaluate {@code maxAge}
     * @throws ServiceUnavailableException if the live read fails and no
     *                                     usable (present and recent enough)
     *                                     last-known status exists
     */
    static <T> ResolvedStatus<T> resolve(
            Supplier<T> read,
            Supplier<Optional<T>> lastKnown,
            Function<T, Instant> measuredAt,
            Duration maxAge,
            Clock clock) {
        try {
            return new ResolvedStatus<>(read.get(), false);
        } catch (Exception ex) {
            T cached = lastKnown.get().orElseThrow(ServiceUnavailableException::new);
            boolean tooOld = Duration.between(measuredAt.apply(cached), clock.instant()).compareTo(maxAge) > 0;
            if (tooOld) {
                throw new ServiceUnavailableException();
            }
            return new ResolvedStatus<>(cached, true);
        }
    }

    record ResolvedStatus<T>(T status, boolean stale) {
    }
}
