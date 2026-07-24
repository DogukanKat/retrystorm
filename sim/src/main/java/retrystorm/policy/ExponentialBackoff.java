package retrystorm.policy;

import retrystorm.engine.Simulator;

/**
 * Retries with a delay that doubles on every attempt, up to a ceiling. Backing
 * off thins out the load a failing dependency sees, but every client that
 * failed at the same moment still retries at the same moment.
 */
public final class ExponentialBackoff implements RetryPolicy {

    private final int maxRetries;
    private final long baseDelayMicros;
    private final long maxDelayMicros;

    public ExponentialBackoff(int maxRetries, long baseDelayMicros, long maxDelayMicros) {
        Backoff.checkBounds(maxRetries, baseDelayMicros, maxDelayMicros);
        this.maxRetries = maxRetries;
        this.baseDelayMicros = baseDelayMicros;
        this.maxDelayMicros = maxDelayMicros;
    }

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        if (attempt > maxRetries) {
            return RetryDecision.giveUp();
        }
        return RetryDecision.retryAfter(Backoff.capMicros(baseDelayMicros, maxDelayMicros, attempt));
    }
}
