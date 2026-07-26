package retrystorm.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class MetricsCollectorTest {

    private static final long SECOND = 1_000_000;

    @Test
    void bucketCountCoversThePartialFinalBucket() {
        assertEquals(3, new MetricsCollector(SECOND, 3 * SECOND).bucketCount());
        assertEquals(4, new MetricsCollector(SECOND, 3 * SECOND + 1).bucketCount());
    }

    @Test
    void filesCountsIntoTheBucketOfTheirTimestamp() {
        MetricsCollector collector = new MetricsCollector(SECOND, 3 * SECOND);
        collector.recordArrival(0);
        collector.recordArrival(SECOND - 1);
        collector.recordArrival(SECOND);
        collector.recordArrival(2 * SECOND + 500);
        List<BucketRow> rows = collector.snapshot();
        assertEquals(2, rows.get(0).offered());
        assertEquals(1, rows.get(1).offered());
        assertEquals(1, rows.get(2).offered());
    }

    @Test
    void keepsEachCounterSeparate() {
        MetricsCollector collector = new MetricsCollector(SECOND, SECOND);
        collector.recordArrival(0);
        collector.recordRetry(0);
        collector.recordRetry(0);
        collector.recordRejection(0);
        collector.recordTimeout(0);
        collector.recordSuccess(0, 5_000);
        BucketRow row = collector.snapshot().get(0);
        assertEquals(1, row.offered());
        assertEquals(2, row.retries());
        assertEquals(1, row.rejections());
        assertEquals(1, row.timeouts());
        assertEquals(1, row.goodput());
    }

    @Test
    void clampsTimestampsAtTheHorizonIntoTheLastBucket() {
        MetricsCollector collector = new MetricsCollector(SECOND, 2 * SECOND);
        collector.recordArrival(2 * SECOND);
        collector.recordArrival(10 * SECOND);
        assertEquals(2, collector.snapshot().get(1).offered());
    }

    @Test
    void computesNearestRankPercentiles() {
        MetricsCollector collector = new MetricsCollector(SECOND, SECOND);
        for (int value = 1; value <= 100; value++) {
            collector.recordSuccess(0, value);
        }
        BucketRow row = collector.snapshot().get(0);
        assertEquals(50, row.p50LatencyMicros());
        assertEquals(99, row.p99LatencyMicros());
    }

    @Test
    void percentileSortsInputAndRoundsTheRankUp() {
        MetricsCollector collector = new MetricsCollector(SECOND, SECOND);
        for (long latency : new long[] {50, 10, 40, 20, 30}) {
            collector.recordSuccess(0, latency);
        }
        BucketRow row = collector.snapshot().get(0);
        // Sorted: [10,20,30,40,50]. p50 rank ceil(2.5)=3 -> 30; floor would give 20.
        assertEquals(30, row.p50LatencyMicros());
        assertEquals(50, row.p99LatencyMicros());
    }

    @Test
    void percentilesAreZeroWithoutSuccesses() {
        MetricsCollector collector = new MetricsCollector(SECOND, SECOND);
        collector.recordTimeout(0);
        BucketRow row = collector.snapshot().get(0);
        assertEquals(0, row.p50LatencyMicros());
        assertEquals(0, row.p99LatencyMicros());
    }

    @Test
    void percentilesAreComputedPerBucket() {
        MetricsCollector collector = new MetricsCollector(SECOND, 2 * SECOND);
        collector.recordSuccess(0, 10);
        collector.recordSuccess(SECOND, 900);
        List<BucketRow> rows = collector.snapshot();
        assertEquals(10, rows.get(0).p99LatencyMicros());
        assertEquals(900, rows.get(1).p99LatencyMicros());
    }

    @Test
    void queueDepthIsStoredPerBucket() {
        MetricsCollector collector = new MetricsCollector(SECOND, 2 * SECOND);
        collector.recordQueueDepth(0, 7);
        collector.recordQueueDepth(1, 42);
        List<BucketRow> rows = collector.snapshot();
        assertEquals(7, rows.get(0).queueDepth());
        assertEquals(42, rows.get(1).queueDepth());
    }

    @Test
    void bucketStartTimesAreExactMultiples() {
        MetricsCollector collector = new MetricsCollector(SECOND, 3 * SECOND);
        List<BucketRow> rows = collector.snapshot();
        assertEquals(0, rows.get(0).bucketStartMicros());
        assertEquals(SECOND, rows.get(1).bucketStartMicros());
        assertEquals(2 * SECOND, rows.get(2).bucketStartMicros());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new MetricsCollector(0, SECOND));
        assertThrows(IllegalArgumentException.class, () -> new MetricsCollector(SECOND, 0));
    }

    @Test
    void rejectsInvalidInput() {
        MetricsCollector collector = new MetricsCollector(SECOND, SECOND);
        assertThrows(IllegalArgumentException.class, () -> collector.recordArrival(-1));
        assertThrows(IllegalArgumentException.class, () -> collector.recordSuccess(0, -1));
        assertThrows(IllegalArgumentException.class, () -> collector.recordQueueDepth(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> collector.recordQueueDepth(5, 0));
    }
}
