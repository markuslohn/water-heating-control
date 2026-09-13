package de.bimalo.homeauto.boundary.modbus;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Test-only CDI bean carrying the exact same Fault Tolerance annotation stack
 * used on every adapter's {@code readStatus()} (see e.g. {@code Elwa2Adapter}),
 * so the retry/circuit-breaker behavior can be verified once, in isolation,
 * without needing a real or simulated Modbus device.
 *
 * <p>
 * Two separately annotated methods are used (rather than one shared method)
 * because SmallRye Fault Tolerance tracks circuit-breaker state per method:
 * reusing one method across independent test scenarios would let an earlier
 * test's failures count towards a later test's circuit-breaker window.
 */
@ApplicationScoped
public class RetryableProbe {

    private final AtomicInteger attempts = new AtomicInteger();

    private volatile int failuresBeforeSuccess = 0;

    public void configureFailuresBeforeSuccess(int failuresBeforeSuccess) {
        this.failuresBeforeSuccess = failuresBeforeSuccess;
        attempts.set(0);
    }

    public int attemptCount() {
        return attempts.get();
    }

    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(6000)
    public String recoversWithinRetryBudget() {
        return attempt();
    }

    @Retry(maxRetries = 2, delay = 200)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5000, successThreshold = 2)
    @Timeout(6000)
    public String exceedsRetryBudget() {
        return attempt();
    }

    private String attempt() {
        int attempt = attempts.incrementAndGet();
        if (attempt <= failuresBeforeSuccess) {
            throw new RuntimeException("Simulated transient failure #" + attempt);
        }
        return "ok";
    }
}
