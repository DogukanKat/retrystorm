package retrystorm.policy;

import retrystorm.engine.Simulator;

/**
 * A fail-fast circuit breaker whose cooldown carries full jitter: on each trip
 * the open duration is the base cooldown plus a uniform draw in
 * {@code [0, cooldown]} from the engine's random source, so the effective
 * cooldown lies in {@code [cooldown, 2*cooldown]}. Independent breakers that
 * trip together therefore half-open at different moments, which softens the
 * synchronized recovery wave a fixed cooldown produces.
 */
public final class JitteredFailFastCircuitBreaker implements RetryPolicy {

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
    private long currentOpenMicros;
    private boolean probeInFlight;
    private boolean reopenPending;

    public JitteredFailFastCircuitBreaker(int maxRetries, long retryDelayMicros, int windowSize,
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
        if (openDurationMicros <= 0 || openDurationMicros == Long.MAX_VALUE) {
            throw new IllegalArgumentException("openDurationMicros must be positive and leave room for jitter");
        }
        this.maxRetries = maxRetries;
        this.retryDelayMicros = retryDelayMicros;
        this.windowSize = windowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.openDurationMicros = openDurationMicros;
        this.failures = new boolean[windowSize];
    }

    /** Whether the breaker is currently open or half-open, for offline analysis only. */
    public boolean isTripped() {
        return state != State.CLOSED;
    }

    @Override
    public boolean admit(Simulator sim) {
        syncState(sim);
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> admitProbe();
        };
    }

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        syncState(sim);
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

    private void syncState(Simulator sim) {
        long now = sim.now();
        if (state == State.OPEN && now - openedAtMicros >= currentOpenMicros) {
            state = State.HALF_OPEN;
            probeInFlight = false;
            reopenPending = false;
        } else if (state == State.HALF_OPEN && reopenPending) {
            trip(sim);
        } else if (state == State.CLOSED && windowCount == windowSize && failureRate() >= failureRateThreshold) {
            trip(sim);
        }
    }

    private void trip(Simulator sim) {
        state = State.OPEN;
        openedAtMicros = sim.now();
        currentOpenMicros = openDurationMicros + sim.random().nextLong(openDurationMicros + 1);
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
