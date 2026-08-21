package de.bimalo.homeauto.boundary.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Verifies the actual runtime behavior of the {@code @Retry}+{@code @CircuitBreaker}
 * annotation stack used on every adapter's {@code readStatus()} method (E1):
 * a transient failure within the retry budget must not be visible to the
 * caller, while exhausting the budget must still surface the failure. This
 * can only be observed through a real CDI-managed bean (a plain {@code new
 * X()} instance bypasses the Fault Tolerance interceptors entirely), hence
 * {@code @QuarkusTest} rather than a plain Mockito unit test.
 */
@QuarkusTest
class RetryBehaviorTest {

    @Inject
    RetryableProbe probe;

    @Test
    void succeedsAfterTransientFailures_withinRetryBudget() {
        probe.configureFailuresBeforeSuccess(1);

        String result = probe.recoversWithinRetryBudget();

        assertEquals("ok", result);
        assertEquals(2, probe.attemptCount());
    }

    @Test
    void propagatesFailure_whenExceedingRetryBudget() {
        probe.configureFailuresBeforeSuccess(Integer.MAX_VALUE);

        assertThrows(RuntimeException.class, () -> probe.exceedsRetryBudget());
        // maxRetries = 2 -> original attempt + 2 retries = 3 total attempts
        assertEquals(3, probe.attemptCount());
    }
}
