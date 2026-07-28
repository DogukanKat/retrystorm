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
import retrystorm.policy.RetryPolicy;

/**
 * Splits one circuit breaker into many independent ones by running the
 * scenario with a growing number of clients, each owning its own breaker, at a
 * fixed total load. A retry-only breaker and a fail-fast breaker are compared.
 * The metric of interest is recovery instability: a fail-fast breaker sheds all
 * load when open, so many that trip together also recover together and can slam
 * the drained server straight back into overload.
 */
public final class HerdExperiment {

    public static final List<Integer> DEFAULT_CLIENT_COUNTS = List.of(1, 10, 50, 100, 200);

    private static final String HEADER = "breaker,client_count,seed,recovery_goodput,recovery_instability";

    private record NamedBreaker(String name, Supplier<RetryPolicy> factory) {
    }

    private HerdExperiment() {
    }

    public static List<HerdRow> run(Scenario base, List<Integer> clientCounts, List<Long> seeds) {
        List<HerdRow> rows = new ArrayList<>();
        for (NamedBreaker breaker : breakers()) {
            for (int clients : clientCounts) {
                for (long seed : seeds) {
                    Scenario scenario = base.withSeed(seed);
                    List<BucketRow> metrics = ScenarioRunner.runWithClients(scenario, breaker.factory(), clients);
                    long from = scenario.spikeEndMicros();
                    long to = scenario.horizonMicros();
                    rows.add(new HerdRow(
                            breaker.name(),
                            clients,
                            seed,
                            WindowStats.meanGoodput(metrics, from, to),
                            WindowStats.stdDevGoodput(metrics, from, to)));
                }
            }
        }
        return rows;
    }

    public static void writeCsv(Path path, List<HerdRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (HerdRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%s,%d,%d,%.3f,%.3f",
                        row.breaker(), row.clientCount(), row.seed(),
                        row.recoveryGoodputPerSecond(), row.recoveryInstability()));
                writer.write('\n');
            }
        }
    }

    private static List<NamedBreaker> breakers() {
        return List.of(
                new NamedBreaker("retry-only", Breakers::retryOnly),
                new NamedBreaker("fail-fast", Breakers::failFast),
                new NamedBreaker("jittered-fail-fast", Breakers::jitteredFailFast));
    }
}
