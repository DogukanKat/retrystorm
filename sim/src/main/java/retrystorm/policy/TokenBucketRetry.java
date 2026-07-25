package retrystorm.policy;

import retrystorm.engine.Simulator;

/**
 * A retry budget. The bucket starts full, each retry spends {@code retryCost}
 * tokens, and each success returns {@code successRefill}. When failures
 * outpace successes the bucket empties and retries stop, which caps the load
 * amplification that turns an overload into a retry storm.
 *
 * <p>Set {@code successRefill} below {@code retryCost} so a burst of retries
 * cannot be paid for by an equal burst of successes.
 */
public final class TokenBucketRetry implements RetryPolicy {

    // Tolerance for the affordability check: refills accumulate rounding error,
    // so a budget that should read exactly full can fall a hair short (ten 0.1
    // refills sum to 0.9999999999999999). Well below any real token value.
    private static final double EPSILON = 1e-9;

    private final int maxRetries;
    private final long retryDelayMicros;
    private final double tokenCapacity;
    private final double retryCost;
    private final double successRefill;
    private double tokens;

    public TokenBucketRetry(int maxRetries, long retryDelayMicros,
                            double tokenCapacity, double retryCost, double successRefill) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative: " + maxRetries);
        }
        if (retryDelayMicros < 0) {
            throw new IllegalArgumentException("retryDelayMicros must be non-negative: " + retryDelayMicros);
        }
        if (tokenCapacity <= 0) {
            throw new IllegalArgumentException("tokenCapacity must be positive: " + tokenCapacity);
        }
        if (retryCost <= 0) {
            throw new IllegalArgumentException("retryCost must be positive: " + retryCost);
        }
        if (successRefill <= 0) {
            throw new IllegalArgumentException("successRefill must be positive: " + successRefill);
        }
        if (retryCost > tokenCapacity) {
            throw new IllegalArgumentException(
                    "retryCost " + retryCost + " exceeds tokenCapacity " + tokenCapacity);
        }
        this.maxRetries = maxRetries;
        this.retryDelayMicros = retryDelayMicros;
        this.tokenCapacity = tokenCapacity;
        this.retryCost = retryCost;
        this.successRefill = successRefill;
        this.tokens = tokenCapacity;
    }

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        if (attempt > maxRetries || tokens < retryCost - EPSILON) {
            return RetryDecision.giveUp();
        }
        tokens -= retryCost;
        return RetryDecision.retryAfter(retryDelayMicros);
    }

    @Override
    public void onSuccess() {
        tokens = Math.min(tokenCapacity, tokens + successRefill);
    }
}
