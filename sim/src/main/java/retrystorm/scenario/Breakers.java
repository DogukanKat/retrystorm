package retrystorm.scenario;

import retrystorm.policy.CircuitBreaker;
import retrystorm.policy.FailFastCircuitBreaker;

/** Shared breaker configuration for the experiments and analyses that compare breaker kinds. */
public final class Breakers {

    static final int MAX_RETRIES = 5;
    static final long RETRY_DELAY_MICROS = 25_000;
    static final int WINDOW_SIZE = 50;
    static final double FAILURE_THRESHOLD = 0.5;
    static final long OPEN_DURATION_MICROS = 2_000_000;

    private Breakers() {
    }

    public static CircuitBreaker retryOnly() {
        return new CircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, WINDOW_SIZE, FAILURE_THRESHOLD,
                OPEN_DURATION_MICROS);
    }

    public static FailFastCircuitBreaker failFast() {
        return new FailFastCircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, WINDOW_SIZE, FAILURE_THRESHOLD,
                OPEN_DURATION_MICROS);
    }
}
