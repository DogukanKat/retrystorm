package retrystorm.scenario;

import retrystorm.policy.CircuitBreaker;
import retrystorm.policy.FailFastCircuitBreaker;
import retrystorm.policy.JitteredFailFastCircuitBreaker;

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
        return retryOnly(OPEN_DURATION_MICROS);
    }

    public static CircuitBreaker retryOnly(long openDurationMicros) {
        return new CircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, WINDOW_SIZE, FAILURE_THRESHOLD,
                openDurationMicros);
    }

    public static FailFastCircuitBreaker failFast() {
        return failFast(OPEN_DURATION_MICROS);
    }

    public static FailFastCircuitBreaker failFast(long openDurationMicros) {
        return new FailFastCircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, WINDOW_SIZE, FAILURE_THRESHOLD,
                openDurationMicros);
    }

    public static JitteredFailFastCircuitBreaker jitteredFailFast() {
        return jitteredFailFast(OPEN_DURATION_MICROS);
    }

    public static JitteredFailFastCircuitBreaker jitteredFailFast(long openDurationMicros) {
        return new JitteredFailFastCircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, WINDOW_SIZE, FAILURE_THRESHOLD,
                openDurationMicros);
    }
}
