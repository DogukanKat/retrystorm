package retrystorm.analysis;

/**
 * One recovery-window bucket, split into first-attempt and multi-attempt
 * successes at the per-attempt timeout. A p99 field is negative when its subset
 * is empty (no successes of that kind in the bucket).
 */
public record SpikeRow(
        String policy,
        double timeSeconds,
        int successes,
        int multiAttempt,
        long p99AllMicros,
        long p99FirstMicros,
        long p99MultiMicros) {
}
