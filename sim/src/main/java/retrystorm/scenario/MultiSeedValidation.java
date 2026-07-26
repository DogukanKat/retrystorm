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
 * Reruns the canonical experiment across several seeds and summarises each
 * run by its mean goodput in the baseline, overload and recovery windows.
 * Running many seeds is what shows the collapse-versus-recovery split is a
 * property of the policies, not of one lucky seed.
 */
public final class MultiSeedValidation {

    public static final List<Long> DEFAULT_SEEDS = List.of(42L, 43L, 44L, 45L, 46L);

    private static final String HEADER =
            "policy,seed,baseline_goodput,overload_goodput,recovery_goodput,overload_retries";

    private MultiSeedValidation() {
    }

    public static List<ValidationRow> run(Scenario base, List<Long> seeds) {
        List<ValidationRow> rows = new ArrayList<>();
        for (long seed : seeds) {
            Scenario scenario = base.withSeed(seed);
            for (NamedPolicy policy : CanonicalExperiment.policies()) {
                List<BucketRow> metrics = ScenarioRunner.run(scenario, policy.factory().get());
                rows.add(new ValidationRow(
                        policy.name(),
                        seed,
                        WindowStats.meanGoodput(metrics, 0, scenario.spikeStartMicros()),
                        WindowStats.meanGoodput(metrics, scenario.spikeStartMicros(), scenario.spikeEndMicros()),
                        WindowStats.meanGoodput(metrics, scenario.spikeEndMicros(), scenario.horizonMicros()),
                        WindowStats.sumRetries(metrics, scenario.spikeStartMicros(), scenario.spikeEndMicros())));
            }
        }
        return rows;
    }

    public static void writeCsv(Path path, List<ValidationRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (ValidationRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%d",
                        row.policy(), row.seed(),
                        row.baselineGoodputPerSecond(), row.overloadGoodputPerSecond(),
                        row.recoveryGoodputPerSecond(), row.overloadRetries()));
                writer.write('\n');
            }
        }
    }
}
