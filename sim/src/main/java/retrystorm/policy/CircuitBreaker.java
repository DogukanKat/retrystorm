package retrystorm.policy;

import retrystorm.engine.Simulator;

/**
 * Stops retrying once recent attempts fail too often. A rolling window of the
 * last {@code windowSize} outcomes trips the breaker open when the failure
 * rate reaches the threshold; after a cooldown it half-opens and lets a single
 * probe retry through. The first outcome seen while half-open then closes the
 * breaker on a success or reopens it on a failure. Only retries are gated:
 * first attempts always reach the server, so their outcomes count too.
 */
public final class CircuitBreaker implements RetryPolicy {

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int maxRetries;
    private final long retryDelayMicros;
    private final int windowSize;
    private final double failureRateThreshold;
    private final long openDurationMicros;

    private final boolean[] failures;
    private int windowIndex;
    private int windowCount;
    private int failureCount;

    private State state = State.CLOSED;
    private long openedAtMicros;
    private boolean probeInFlight;
    private boolean probeFailed;

    public CircuitBreaker(int maxRetries, long retryDelayMicros, int windowSize,
                          double failureRateThreshold, long openDurationMicros) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative: " + maxRetries);
        }
        if (retryDelayMicros < 0) {
            throw new IllegalArgumentException("retryDelayMicros must be non-negative: " + retryDelayMicros);
        }
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive: " + windowSize);
        }
        if (failureRateThreshold <= 0 || failureRateThreshold > 1) {
            throw new IllegalArgumentException("failureRateThreshold must be in (0, 1]: " + failureRateThreshold);
        }
        if (openDurationMicros <= 0) {
            throw new IllegalArgumentException("openDurationMicros must be positive: " + openDurationMicros);
        }
        this.maxRetries = maxRetries;
        this.retryDelayMicros = retryDelayMicros;
        this.windowSize = windowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.openDurationMicros = openDurationMicros;
        this.failures = new boolean[windowSize];
    }

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        long now = sim.now();
        if (state == State.OPEN && now - openedAtMicros >= openDurationMicros) {
            state = State.HALF_OPEN;
            probeInFlight = false;
            probeFailed = false;
        }
        if (state == State.HALF_OPEN && probeFailed) {
            state = State.OPEN;
            openedAtMicros = now;
            probeInFlight = false;
            probeFailed = false;
            return RetryDecision.giveUp();
        }
        if (state == State.OPEN) {
            return RetryDecision.giveUp();
        }
        if (state == State.HALF_OPEN) {
            if (probeInFlight) {
                return RetryDecision.giveUp();
            }
            probeInFlight = true;
            return RetryDecision.retryAfter(retryDelayMicros);
        }
        if (windowCount == windowSize && failureRate() >= failureRateThreshold) {
            state = State.OPEN;
            openedAtMicros = now;
            return RetryDecision.giveUp();
        }
        return attempt <= maxRetries ? RetryDecision.retryAfter(retryDelayMicros) : RetryDecision.giveUp();
    }

    @Override
    public void onSuccess() {
        record(false);
        if (state == State.HALF_OPEN) {
            reset();
        }
    }

    @Override
    public void onFailure() {
        record(true);
        if (state == State.HALF_OPEN) {
            probeFailed = true;
        }
    }

    private void record(boolean failure) {
        if (windowCount == windowSize && failures[windowIndex]) {
            failureCount--;
        }
        failures[windowIndex] = failure;
        if (failure) {
            failureCount++;
        }
        windowIndex = (windowIndex + 1) % windowSize;
        if (windowCount < windowSize) {
            windowCount++;
        }
    }

    private double failureRate() {
        return (double) failureCount / windowCount;
    }

    private void reset() {
        state = State.CLOSED;
        windowIndex = 0;
        windowCount = 0;
        failureCount = 0;
        probeInFlight = false;
        probeFailed = false;
    }
}
