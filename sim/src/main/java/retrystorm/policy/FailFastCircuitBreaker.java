package retrystorm.policy;

import retrystorm.engine.Simulator;

/**
 * A circuit breaker that fails fast: while it is open it admits no requests at
 * all, not just retries. Load is shed at the source, so an open breaker lets
 * the server drain completely. After a cooldown it half-opens for a single
 * probe; a probe success closes it and a failure reopens it.
 *
 * <p>Unlike a retry-only breaker, many independent fail-fast breakers that
 * tripped together also recover together, which is how a synchronized probe
 * wave can slam a just-drained server straight back into overload.
 */
public final class FailFastCircuitBreaker implements RetryPolicy {

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
    private boolean reopenPending;

    public FailFastCircuitBreaker(int maxRetries, long retryDelayMicros, int windowSize,
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
    public boolean admit(Simulator sim) {
        syncState(sim.now());
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> admitProbe();
        };
    }

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        syncState(sim.now());
        if (state != State.CLOSED) {
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
            reopenPending = true;
        }
    }

    private boolean admitProbe() {
        if (probeInFlight) {
            return false;
        }
        probeInFlight = true;
        return true;
    }

    private void syncState(long now) {
        if (state == State.OPEN && now - openedAtMicros >= openDurationMicros) {
            state = State.HALF_OPEN;
            probeInFlight = false;
            reopenPending = false;
        } else if (state == State.HALF_OPEN && reopenPending) {
            trip(now);
        } else if (state == State.CLOSED && windowCount == windowSize && failureRate() >= failureRateThreshold) {
            trip(now);
        }
    }

    private void trip(long now) {
        state = State.OPEN;
        openedAtMicros = now;
        probeInFlight = false;
        reopenPending = false;
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
        reopenPending = false;
    }
}
