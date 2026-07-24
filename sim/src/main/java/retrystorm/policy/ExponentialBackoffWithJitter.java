package retrystorm.policy;

import retrystorm.engine.Simulator;

/**
 * Exponential backoff with full jitter: the delay is drawn uniformly from
 * {@code [0, cap]} rather than being the cap itself. Clients that failed
 * together come back at different moments instead of in lockstep, which is
 * what stops a retry wave from re-forming on every cycle.
 */
public final class ExponentialBackoffWithJitter implements RetryPolicy {

    private final int maxRetries;
    private final long baseDelayMicros;
    private final long maxDelayMicros;

    public ExponentialBackoffWithJitter(int maxRetries, long baseDelayMicros, long maxDelayMicros) {
        Backoff.checkBounds(maxRetries, baseDelayMicros, maxDelayMicros);
        if (maxDelayMicros == Long.MAX_VALUE) {
            throw new IllegalArgumentException("maxDelayMicros must leave room for an inclusive draw");
        }
        this.maxRetries = maxRetries;
        this.baseDelayMicros = baseDelayMicros;
        this.maxDelayMicros = maxDelayMicros;
    }

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        if (attempt > maxRetries) {
            return RetryDecision.giveUp();
        }
        long cap = Backoff.capMicros(baseDelayMicros, maxDelayMicros, attempt);
        return RetryDecision.retryAfter(sim.random().nextLong(cap + 1));
    }
}
