package retrystorm.policy;

import retrystorm.engine.Simulator;

/**
 * Retries up to {@code maxRetries} times, waiting the same amount every time.
 * The naive policy: it neither backs off as failures persist nor spreads
 * retries out, so every failure adds load at a fixed offset.
 */
public final class FixedRetry implements RetryPolicy {

    private final int maxRetries;
    private final long retryDelayMicros;

    public FixedRetry(int maxRetries, long retryDelayMicros) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative: " + maxRetries);
        }
        if (retryDelayMicros < 0) {
            throw new IllegalArgumentException("retryDelayMicros must be non-negative: " + retryDelayMicros);
        }
        this.maxRetries = maxRetries;
        this.retryDelayMicros = retryDelayMicros;
    }

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        return attempt <= maxRetries ? RetryDecision.retryAfter(retryDelayMicros) : RetryDecision.giveUp();
    }
}
