package retrystorm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import retrystorm.analysis.BreakerStateAnalysis;
import retrystorm.analysis.BreakerStateRow;
import retrystorm.analysis.OpenTraceAnalysis;
import retrystorm.analysis.OpenTraceRow;
import retrystorm.analysis.SpikeAnalysis;
import retrystorm.analysis.SpikeRow;
import retrystorm.metrics.BucketRow;
import retrystorm.metrics.CsvWriter;
import retrystorm.metrics.RunResult;
import retrystorm.scenario.CanonicalExperiment;
import retrystorm.scenario.CanonicalExperiment.NamedPolicy;
import retrystorm.scenario.ClientCountSweep;
import retrystorm.scenario.CooldownRow;
import retrystorm.scenario.CooldownSweep;
import retrystorm.scenario.HerdExperiment;
import retrystorm.scenario.HerdRow;
import retrystorm.scenario.MultiSeedValidation;
import retrystorm.scenario.PhaseExperiment;
import retrystorm.scenario.PhaseRow;
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
            case "herd" -> runHerd();
            case "phase" -> runPhase();
            case "analyze-spikes" -> runSpikeAnalysis();
            case "analyze-breaker-state" -> runBreakerStateAnalysis();
            case "analyze-cooldown" -> runCooldownSweep();
            case "analyze-open-trace" -> runOpenTrace();
            default -> {
                System.err.println("unknown mode: " + mode + " (expected 'canonical', 'validate', 'sweep', "
                        + "'herd', 'phase', 'analyze-spikes', 'analyze-breaker-state', 'analyze-cooldown' "
                        + "or 'analyze-open-trace')");
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

    private static void runHerd() throws IOException {
        List<HerdRow> rows = HerdExperiment.run(CanonicalExperiment.scenario(),
                HerdExperiment.DEFAULT_CLIENT_COUNTS, MultiSeedValidation.DEFAULT_SEEDS);
        HerdExperiment.writeCsv(RESULTS_DIR.resolve("herd.csv"), rows);
        System.out.println("wrote herd.csv (" + rows.size() + " rows)");
    }

    private static void runPhase() throws IOException {
        List<PhaseRow> rows = PhaseExperiment.run(CanonicalExperiment.scenario(),
                PhaseExperiment.DEFAULT_UTILISATIONS, MultiSeedValidation.DEFAULT_SEEDS);
        PhaseExperiment.writeCsv(RESULTS_DIR.resolve("phase.csv"), rows);
        System.out.println("wrote phase.csv (" + rows.size() + " rows)");
    }

    private static void runSpikeAnalysis() throws IOException {
        List<SpikeRow> rows = SpikeAnalysis.run(CanonicalExperiment.scenario());
        SpikeAnalysis.writeCsv(RESULTS_DIR.resolve("spike_analysis.csv"), rows);
        System.out.println("wrote spike_analysis.csv (" + rows.size() + " rows)");
    }

    private static void runBreakerStateAnalysis() throws IOException {
        List<BreakerStateRow> rows = BreakerStateAnalysis.run(CanonicalExperiment.scenario(),
                PhaseExperiment.DEFAULT_UTILISATIONS, MultiSeedValidation.DEFAULT_SEEDS);
        BreakerStateAnalysis.writeCsv(RESULTS_DIR.resolve("breaker_state.csv"), rows);
        System.out.println("wrote breaker_state.csv (" + rows.size() + " rows)");
    }

    private static void runCooldownSweep() throws IOException {
        List<CooldownRow> rows = CooldownSweep.run(CanonicalExperiment.scenario(),
                CooldownSweep.DEFAULT_COOLDOWNS_MICROS, HerdExperiment.DEFAULT_CLIENT_COUNTS,
                MultiSeedValidation.DEFAULT_SEEDS);
        CooldownSweep.writeCsv(RESULTS_DIR.resolve("cooldown.csv"), rows);
        System.out.println("wrote cooldown.csv (" + rows.size() + " rows)");
    }

    private static void runOpenTrace() throws IOException {
        List<OpenTraceRow> rows = OpenTraceAnalysis.run(
                CanonicalExperiment.scenario(), MultiSeedValidation.DEFAULT_SEEDS);
        OpenTraceAnalysis.writeCsv(RESULTS_DIR.resolve("open_trace.csv"), rows);
        System.out.println("wrote open_trace.csv (" + rows.size() + " rows)");
    }
}
