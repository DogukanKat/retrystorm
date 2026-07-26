package retrystorm.metrics;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Writes metric rows as CSV with a fixed column order and a fixed locale, so
 * the same run produces byte-identical output.
 */
public final class CsvWriter {

    static final String HEADER =
            "policy,time_s,offered,goodput,rejections,timeouts,retries,queue_depth,p50_latency_ms,p99_latency_ms";

    private CsvWriter() {
    }

    public static void writeSingle(Path path, RunResult result) throws IOException {
        write(path, List.of(result));
    }

    public static void writeCombined(Path path, List<RunResult> results) throws IOException {
        write(path, results);
    }

    private static void write(Path path, List<RunResult> results) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (RunResult result : results) {
                for (BucketRow row : result.rows()) {
                    writer.write(formatRow(result.policy(), row));
                    writer.write('\n');
                }
            }
        }
    }

    private static String formatRow(String policy, BucketRow row) {
        return String.format(
                Locale.ROOT,
                "%s,%.3f,%d,%d,%d,%d,%d,%d,%s,%s",
                policy,
                row.bucketStartMicros() / 1_000_000.0,
                row.offered(),
                row.goodput(),
                row.rejections(),
                row.timeouts(),
                row.retries(),
                row.queueDepth(),
                latencyMillis(row.goodput(), row.p50LatencyMicros()),
                latencyMillis(row.goodput(), row.p99LatencyMicros()));
    }

    /** Empty when the bucket had no successes, so latency reads as undefined rather than zero. */
    private static String latencyMillis(long goodput, long latencyMicros) {
        return goodput == 0 ? "" : String.format(Locale.ROOT, "%.3f", latencyMicros / 1000.0);
    }
}
