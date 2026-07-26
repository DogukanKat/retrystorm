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
 * Sweeps the breaker cooldown for the herd experiment. Recovery goodput is
 * reported alongside instability because a long cooldown does not reduce
 * oscillation so much as remove recovery: once a breaker trips, a cooldown
 * longer than the recovery window keeps it open to the horizon, so it sheds
 * all load and its goodput collapses.
 */
public final class CooldownSweep {

    private static final long SECOND = 1_000_000;

    public static final List<Long> DEFAULT_COOLDOWNS_MICROS = List.of(2 * SECOND, 10 * SECOND, 60 * SECOND);

    private static final String HEADER =
            "breaker,cooldown_s,client_count,seed,recovery_goodput,recovery_instability";

    private record NamedBreaker(String name, java.util.function.LongFunction<RetryPolicy> factory) {
    }

    private CooldownSweep() {
    }

    public static List<CooldownRow> run(Scenario base, List<Long> cooldownsMicros,
                                        List<Integer> clientCounts, List<Long> seeds) {
        List<CooldownRow> rows = new ArrayList<>();
        for (NamedBreaker breaker : breakers()) {
            for (long cooldown : cooldownsMicros) {
                for (int clients : clientCounts) {
                    for (long seed : seeds) {
                        Scenario scenario = base.withSeed(seed);
                        Supplier<RetryPolicy> factory = () -> breaker.factory().apply(cooldown);
                        List<BucketRow> metrics = ScenarioRunner.runWithClients(scenario, factory, clients);
                        long from = scenario.spikeEndMicros();
                        long to = scenario.horizonMicros();
                        rows.add(new CooldownRow(breaker.name(), cooldown, clients, seed,
                                WindowStats.meanGoodput(metrics, from, to),
                                WindowStats.stdDevGoodput(metrics, from, to)));
                    }
                }
            }
        }
        return rows;
    }

    public static void writeCsv(Path path, List<CooldownRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (CooldownRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%s,%d,%d,%d,%.3f,%.3f",
                        row.breaker(), row.cooldownMicros() / SECOND, row.clientCount(), row.seed(),
                        row.recoveryGoodputPerSecond(), row.recoveryInstability()));
                writer.write('\n');
            }
        }
    }

    private static List<NamedBreaker> breakers() {
        return List.of(
                new NamedBreaker("retry-only", Breakers::retryOnly),
                new NamedBreaker("fail-fast", Breakers::failFast));
    }
}
