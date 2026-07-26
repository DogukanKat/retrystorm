package retrystorm.scenario;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrystorm.metrics.BucketRow;
import retrystorm.scenario.CanonicalExperiment.NamedPolicy;

/**
 * Sweeps the canonical scenario across client counts, keeping the offered load
 * per client fixed so total load scales with the count. The canonical scenario
 * is defined to be {@link #CANONICAL_CLIENTS} clients. Only backoff-with-jitter
 * and the token bucket are swept, to show where backoff stops being enough:
 * few clients let backoff spread retries thinly, many do not.
 */
public final class ClientCountSweep {

    public static final int CANONICAL_CLIENTS = 100;
    public static final List<Integer> DEFAULT_CLIENT_COUNTS = List.of(25, 50, 75, 100);

    private static final List<String> SWEPT_POLICIES = List.of("backoff-jitter", "token-bucket");
    private static final String HEADER = "client_count,policy,baseline_goodput,recovery_goodput";

    private ClientCountSweep() {
    }

    public static List<SweepRow> run(Scenario canonical, List<Integer> clientCounts) {
        double perClientBase = canonical.baseRatePerSecond() / CANONICAL_CLIENTS;
        double perClientSpike = canonical.spikeRatePerSecond() / CANONICAL_CLIENTS;

        List<SweepRow> rows = new ArrayList<>();
        for (int clients : clientCounts) {
            Scenario scaled = canonical.withArrivalRates(clients * perClientBase, clients * perClientSpike);
            for (NamedPolicy policy : sweptPolicies()) {
                List<BucketRow> metrics = ScenarioRunner.run(scaled, policy.factory().get());
                rows.add(new SweepRow(
                        clients,
                        policy.name(),
                        WindowStats.meanGoodput(metrics, 0, scaled.spikeStartMicros()),
                        WindowStats.meanGoodput(metrics, scaled.spikeEndMicros(), scaled.horizonMicros())));
            }
        }
        return rows;
    }

    public static void writeCsv(Path path, List<SweepRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (SweepRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%d,%s,%.3f,%.3f",
                        row.clientCount(), row.policy(),
                        row.baselineGoodputPerSecond(), row.recoveryGoodputPerSecond()));
                writer.write('\n');
            }
        }
    }

    private static List<NamedPolicy> sweptPolicies() {
        return CanonicalExperiment.policies().stream()
                .filter(policy -> SWEPT_POLICIES.contains(policy.name()))
                .toList();
    }
}
