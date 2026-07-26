package retrystorm.analysis;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import retrystorm.engine.Simulator;
import retrystorm.metrics.MetricsCollector;
import retrystorm.policy.RetryPolicy;
import retrystorm.scenario.CanonicalExperiment;
import retrystorm.scenario.CanonicalExperiment.NamedPolicy;
import retrystorm.scenario.Scenario;
import retrystorm.sim.Client;
import retrystorm.sim.ExponentialServiceTime;
import retrystorm.sim.Server;

/**
 * Offline analysis of the recovery-window p99 spikes. It reruns the recovering
 * policies and splits each recovery bucket's success latencies at the
 * per-attempt timeout: a success under the timeout completed on its first
 * attempt, one at or above it must have timed out and completed on a retry.
 * That split is exact only while rejections are negligible, which they are in
 * the recovery window. Not part of the canonical experiment.
 */
public final class SpikeAnalysis {

    private static final List<String> ANALYSED = List.of("no-retry", "token-bucket", "circuit-breaker");
    private static final long EMPTY = -1L;
    private static final String HEADER =
            "policy,time_s,successes,multi_attempt,p99_all_ms,p99_first_ms,p99_multi_ms";

    private SpikeAnalysis() {
    }

    public static List<SpikeRow> run(Scenario scenario) {
        List<SpikeRow> rows = new ArrayList<>();
        for (NamedPolicy policy : CanonicalExperiment.policies()) {
            if (ANALYSED.contains(policy.name())) {
                analyse(policy.name(), scenario, policy.factory(), rows);
            }
        }
        return rows;
    }

    public static void writeCsv(Path path, List<SpikeRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (SpikeRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%s,%.3f,%d,%d,%s,%s,%s",
                        row.policy(), row.timeSeconds(), row.successes(), row.multiAttempt(),
                        millis(row.p99AllMicros()), millis(row.p99FirstMicros()), millis(row.p99MultiMicros())));
                writer.write('\n');
            }
        }
    }

    private static void analyse(String name, Scenario scenario, Supplier<RetryPolicy> factory, List<SpikeRow> out) {
        MetricsCollector metrics = rerun(scenario, factory);
        long timeoutMicros = scenario.attemptTimeoutMicros();
        int recoveryStart = (int) (scenario.spikeEndMicros() / scenario.bucketMicros());

        for (int bucket = recoveryStart; bucket < metrics.bucketCount(); bucket++) {
            List<Long> latencies = metrics.latenciesMicros(bucket);
            if (latencies.isEmpty()) {
                continue;
            }
            List<Long> first = latencies.stream().filter(l -> l < timeoutMicros).toList();
            List<Long> multi = latencies.stream().filter(l -> l >= timeoutMicros).toList();
            out.add(new SpikeRow(
                    name,
                    (double) bucket * scenario.bucketMicros() / 1_000_000.0,
                    latencies.size(),
                    multi.size(),
                    percentile(latencies),
                    percentile(first),
                    percentile(multi)));
        }
    }

    private static MetricsCollector rerun(Scenario scenario, Supplier<RetryPolicy> factory) {
        Simulator sim = new Simulator(scenario.seed());
        MetricsCollector metrics = new MetricsCollector(scenario.bucketMicros(), scenario.horizonMicros());
        Server server = new Server(sim, scenario.workers(), scenario.queueCapacity(),
                new ExponentialServiceTime(scenario.meanServiceMicros()));
        new Client(sim, server, factory.get(), metrics, scenario.rateSchedule(),
                scenario.attemptTimeoutMicros(), scenario.maxAttempts()).start();
        sim.run(scenario.horizonMicros());
        return metrics;
    }

    private static long percentile(List<Long> values) {
        if (values.isEmpty()) {
            return EMPTY;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(0.99 * sorted.size());
        int index = Math.min(sorted.size() - 1, Math.max(0, rank - 1));
        return sorted.get(index);
    }

    private static String millis(long micros) {
        return micros < 0 ? "" : String.format(Locale.ROOT, "%.3f", micros / 1000.0);
    }
}
