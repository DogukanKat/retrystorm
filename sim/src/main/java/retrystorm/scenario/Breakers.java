package retrystorm.scenario;

import retrystorm.policy.CircuitBreaker;
import retrystorm.policy.FailFastCircuitBreaker;

/** Shared breaker configuration for the experiments that compare breaker kinds. */
final class Breakers {

    static final int MAX_RETRIES = 5;
    static final long RETRY_DELAY_MICROS = 25_000;
    static final int WINDOW_SIZE = 50;
    static final double FAILURE_THRESHOLD = 0.5;
    static final long OPEN_DURATION_MICROS = 2_000_000;

    private Breakers() {
    }

    static CircuitBreaker retryOnly() {
        return new CircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, WINDOW_SIZE, FAILURE_THRESHOLD,
                OPEN_DURATION_MICROS);
    }

    static FailFastCircuitBreaker failFast() {
        return new FailFastCircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, WINDOW_SIZE, FAILURE_THRESHOLD,
                OPEN_DURATION_MICROS);
    }
}
