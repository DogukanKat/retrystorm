package retrystorm.metrics;

/** Aggregated metrics for one time bucket. Counts are totals within the bucket. */
public record BucketRow(
        int bucketIndex,
        long bucketStartMicros,
        long offered,
        long goodput,
        long rejections,
        long timeouts,
        long retries,
        int queueDepth,
        long p50LatencyMicros,
        long p99LatencyMicros) {
}
