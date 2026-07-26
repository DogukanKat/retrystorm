package retrystorm.scenario;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import retrystorm.metrics.BucketRow;
import retrystorm.policy.FixedRetry;
import retrystorm.policy.NoRetry;
import retrystorm.policy.RetryPolicy;

/**
 * Maps where a circuit breaker stops helping. Baseline utilisation is swept
 * with the overload spike held fixed, comparing no-retry against a retry-only
 * and a fail-fast breaker, each split across {@link #HERD_CLIENTS} independent
 * clients. At low utilisation the breakers recover cleanly; as the baseline
 * approaches capacity they grow unstable and eventually collapse the baseline
 * itself, which no-retry does not.
 */
public final class PhaseExperiment {

    public static final List<Double> DEFAULT_UTILISATIONS =
            List.of(0.5, 0.6, 0.7, 0.8, 0.82, 0.85, 0.88, 0.9, 0.92);
    public static final int HERD_CLIENTS = 100;

    private static final String HEADER =
            "policy,utilisation,seed,baseline_goodput,recovery_goodput,recovery_instability";

    private record NamedPolicy(String name, Supplier<RetryPolicy> factory) {
    }

    private PhaseExperiment() {
    }

    public static List<PhaseRow> run(Scenario base, List<Double> utilisations, List<Long> seeds) {
        double capacity = base.workers() * 1_000_000.0 / base.meanServiceMicros();

        List<PhaseRow> rows = new ArrayList<>();
        for (NamedPolicy policy : policies()) {
            for (double utilisation : utilisations) {
                double baseRate = utilisation * capacity;
                for (long seed : seeds) {
                    Scenario scenario = base.withSeed(seed)
                            .withArrivalRates(baseRate, base.spikeRatePerSecond());
                    List<BucketRow> metrics =
                            ScenarioRunner.runWithClients(scenario, policy.factory(), HERD_CLIENTS);
                    long from = scenario.spikeEndMicros();
                    long to = scenario.horizonMicros();
                    rows.add(new PhaseRow(
                            policy.name(),
                            utilisation,
                            seed,
                            WindowStats.meanGoodput(metrics, 0, scenario.spikeStartMicros()),
                            WindowStats.meanGoodput(metrics, from, to),
                            WindowStats.stdDevGoodput(metrics, from, to)));
                }
            }
        }
        return rows;
    }

    public static void writeCsv(Path path, List<PhaseRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (PhaseRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%s,%.2f,%d,%.3f,%.3f,%.3f",
                        row.policy(), row.utilisation(), row.seed(),
                        row.baselineGoodputPerSecond(), row.recoveryGoodputPerSecond(),
                        row.recoveryInstability()));
                writer.write('\n');
            }
        }
    }

    private static List<NamedPolicy> policies() {
        return List.of(
                new NamedPolicy("no-retry", NoRetry::new),
                new NamedPolicy("retry-only", Breakers::retryOnly),
                new NamedPolicy("fail-fast", Breakers::failFast),
                new NamedPolicy("fixed-retry", () -> new FixedRetry(Breakers.MAX_RETRIES, Breakers.RETRY_DELAY_MICROS)));
    }
}
