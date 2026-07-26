package retrystorm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import retrystorm.metrics.BucketRow;
import retrystorm.metrics.CsvWriter;
import retrystorm.metrics.RunResult;
import retrystorm.scenario.CanonicalExperiment;
import retrystorm.scenario.CanonicalExperiment.NamedPolicy;
import retrystorm.scenario.ClientCountSweep;
import retrystorm.scenario.MultiSeedValidation;
import retrystorm.scenario.Scenario;
import retrystorm.scenario.ScenarioRunner;
import retrystorm.scenario.SweepRow;
import retrystorm.scenario.ValidationRow;

/**
 * Entry point for the experiments. Writes CSVs into {@code ../results}
 * (relative to the Gradle project). The first argument selects the mode:
 * {@code canonical} (default) or {@code validate}.
 */
public final class Main {

    private static final Path RESULTS_DIR = Path.of("../results");

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "canonical";
        switch (mode) {
            case "canonical" -> runCanonical();
            case "validate" -> runValidation();
            case "sweep" -> runSweep();
            default -> {
                System.err.println("unknown mode: " + mode + " (expected 'canonical', 'validate' or 'sweep')");
                System.exit(2);
            }
        }
    }

    private static void runCanonical() throws IOException {
        Scenario scenario = CanonicalExperiment.scenario();
        List<RunResult> results = new ArrayList<>();
        for (NamedPolicy policy : CanonicalExperiment.policies()) {
            List<BucketRow> rows = ScenarioRunner.run(scenario, policy.factory().get());
            RunResult result = new RunResult(policy.name(), rows);
            results.add(result);
            CsvWriter.writeSingle(RESULTS_DIR.resolve(policy.name() + ".csv"), result);
            System.out.println("wrote " + policy.name() + ".csv");
        }
        CsvWriter.writeCombined(RESULTS_DIR.resolve("combined.csv"), results);
        System.out.println("wrote combined.csv (" + results.size() + " policies)");
    }

    private static void runValidation() throws IOException {
        List<ValidationRow> rows = MultiSeedValidation.run(
                CanonicalExperiment.scenario(), MultiSeedValidation.DEFAULT_SEEDS);
        MultiSeedValidation.writeCsv(RESULTS_DIR.resolve("validation.csv"), rows);
        System.out.println("wrote validation.csv (" + rows.size() + " rows across "
                + MultiSeedValidation.DEFAULT_SEEDS.size() + " seeds)");
    }

    private static void runSweep() throws IOException {
        List<SweepRow> rows = ClientCountSweep.run(
                CanonicalExperiment.scenario(), ClientCountSweep.DEFAULT_CLIENT_COUNTS);
        ClientCountSweep.writeCsv(RESULTS_DIR.resolve("sweep.csv"), rows);
        System.out.println("wrote sweep.csv (" + rows.size() + " rows)");
    }
}
