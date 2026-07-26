package retrystorm.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates counts into fixed-width time buckets and, at snapshot time,
 * derives latency percentiles per bucket. A metric is filed into the bucket
 * its timestamp falls in, so collection is a pure function of the events fed
 * to it and stays deterministic.
 */
public final class MetricsCollector {

    private final long bucketMicros;
    private final int bucketCount;
    private final long[] offered;
    private final long[] goodput;
    private final long[] rejections;
    private final long[] timeouts;
    private final long[] retries;
    private final int[] queueDepth;
    private final List<List<Long>> latencyMicros;

    public MetricsCollector(long bucketMicros, long horizonMicros) {
        if (bucketMicros <= 0) {
            throw new IllegalArgumentException("bucketMicros must be positive: " + bucketMicros);
        }
        if (horizonMicros <= 0) {
            throw new IllegalArgumentException("horizonMicros must be positive: " + horizonMicros);
        }
        this.bucketMicros = bucketMicros;
        this.bucketCount = (int) ((horizonMicros + bucketMicros - 1) / bucketMicros);
        this.offered = new long[bucketCount];
        this.goodput = new long[bucketCount];
        this.rejections = new long[bucketCount];
        this.timeouts = new long[bucketCount];
        this.retries = new long[bucketCount];
        this.queueDepth = new int[bucketCount];
        this.latencyMicros = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            latencyMicros.add(new ArrayList<>());
        }
    }

    public int bucketCount() {
        return bucketCount;
    }

    /**
     * Read-only view of the success latencies recorded in {@code bucketIndex},
     * for offline analysis only. This is not part of the metrics contract:
     * analysis code may depend on it, simulation code must not.
     */
    public List<Long> latenciesMicros(int bucketIndex) {
        return List.copyOf(latencyMicros.get(bucketIndex));
    }

    public void recordArrival(long nowMicros) {
        offered[bucketOf(nowMicros)]++;
    }

    public void recordRetry(long nowMicros) {
        retries[bucketOf(nowMicros)]++;
    }

    public void recordRejection(long nowMicros) {
        rejections[bucketOf(nowMicros)]++;
    }

    public void recordTimeout(long nowMicros) {
        timeouts[bucketOf(nowMicros)]++;
    }

    public void recordSuccess(long nowMicros, long latencyMicros) {
        if (latencyMicros < 0) {
            throw new IllegalArgumentException("latency must be non-negative: " + latencyMicros);
        }
        int bucket = bucketOf(nowMicros);
        goodput[bucket]++;
        this.latencyMicros.get(bucket).add(latencyMicros);
    }

    /** Records the queue depth sampled at the close of {@code bucketIndex}. */
    public void recordQueueDepth(int bucketIndex, int depth) {
        if (bucketIndex < 0 || bucketIndex >= bucketCount) {
            throw new IndexOutOfBoundsException("bucketIndex out of range: " + bucketIndex);
        }
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be non-negative: " + depth);
        }
        queueDepth[bucketIndex] = depth;
    }

    public List<BucketRow> snapshot() {
        List<BucketRow> rows = new ArrayList<>(bucketCount);
        for (int b = 0; b < bucketCount; b++) {
            List<Long> latencies = latencyMicros.get(b);
            rows.add(new BucketRow(
                    b,
                    (long) b * bucketMicros,
                    offered[b],
                    goodput[b],
                    rejections[b],
                    timeouts[b],
                    retries[b],
                    queueDepth[b],
                    percentileMicros(latencies, 50),
                    percentileMicros(latencies, 99)));
        }
        return rows;
    }

    private int bucketOf(long nowMicros) {
        if (nowMicros < 0) {
            throw new IllegalArgumentException("time must be non-negative: " + nowMicros);
        }
        return Math.min((int) (nowMicros / bucketMicros), bucketCount - 1);
    }

    /** Nearest-rank percentile: the smallest sample at or above the given rank. */
    private static long percentileMicros(List<Long> values, int percentile) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        int index = Math.min(sorted.size() - 1, Math.max(0, rank - 1));
        return sorted.get(index);
    }
}
