package retrystorm.policy;

/** What a {@link RetryPolicy} decided: give up, or retry after {@code delayMicros}. */
public record RetryDecision(boolean retry, long delayMicros) {

    public RetryDecision {
        if (retry && delayMicros < 0) {
            throw new IllegalArgumentException("retry delay must be non-negative: " + delayMicros);
        }
        if (!retry && delayMicros != 0) {
            throw new IllegalArgumentException("give-up decision must carry no delay: " + delayMicros);
        }
    }

    public static RetryDecision giveUp() {
        return new RetryDecision(false, 0L);
    }

    public static RetryDecision retryAfter(long delayMicros) {
        return new RetryDecision(true, delayMicros);
    }
}
