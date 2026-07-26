package retrystorm.scenario;

import java.util.List;

import retrystorm.metrics.BucketRow;

/** Goodput statistics over a half-open time window {@code [fromMicros, toMicros)}. */
final class WindowStats {

    private WindowStats() {
    }

    static double meanGoodput(List<BucketRow> rows, long fromMicros, long toMicros) {
        long sum = 0;
        int count = 0;
        for (BucketRow row : rows) {
            if (inWindow(row, fromMicros, toMicros)) {
                sum += row.goodput();
                count++;
            }
        }
        return count == 0 ? 0.0 : (double) sum / count;
    }

    /**
     * Standard deviation of per-bucket goodput in the window: how much goodput
     * swings from second to second, so a steady recovery scores low and an
     * oscillating one scores high.
     */
    static double stdDevGoodput(List<BucketRow> rows, long fromMicros, long toMicros) {
        double mean = meanGoodput(rows, fromMicros, toMicros);
        double sumSquares = 0;
        int count = 0;
        for (BucketRow row : rows) {
            if (inWindow(row, fromMicros, toMicros)) {
                double delta = row.goodput() - mean;
                sumSquares += delta * delta;
                count++;
            }
        }
        return count == 0 ? 0.0 : Math.sqrt(sumSquares / count);
    }

    static long sumRetries(List<BucketRow> rows, long fromMicros, long toMicros) {
        long sum = 0;
        for (BucketRow row : rows) {
            if (inWindow(row, fromMicros, toMicros)) {
                sum += row.retries();
            }
        }
        return sum;
    }

    private static boolean inWindow(BucketRow row, long fromMicros, long toMicros) {
        return row.bucketStartMicros() >= fromMicros && row.bucketStartMicros() < toMicros;
    }
}
