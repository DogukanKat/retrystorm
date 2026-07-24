package retrystorm.policy;

final class Backoff {

    private Backoff() {
    }

    /**
     * Delay bound for {@code attempt}: {@code base} doubled once per attempt
     * already made, never above {@code max}. Doubling stops before it can
     * overflow, since a value past half of {@code max} is already capped.
     */
    static long capMicros(long baseDelayMicros, long maxDelayMicros, int attempt) {
        long delay = baseDelayMicros;
        for (int i = 1; i < attempt; i++) {
            if (delay > maxDelayMicros / 2) {
                return maxDelayMicros;
            }
            delay *= 2;
        }
        return delay;
    }

    static void checkBounds(int maxRetries, long baseDelayMicros, long maxDelayMicros) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative: " + maxRetries);
        }
        if (baseDelayMicros <= 0) {
            throw new IllegalArgumentException("baseDelayMicros must be positive: " + baseDelayMicros);
        }
        if (maxDelayMicros < baseDelayMicros) {
            throw new IllegalArgumentException(
                    "maxDelayMicros " + maxDelayMicros + " is below baseDelayMicros " + baseDelayMicros);
        }
    }
}
