package retrystorm.analysis;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import retrystorm.engine.Simulator;
import retrystorm.metrics.MetricsCollector;
import retrystorm.policy.CircuitBreaker;
import retrystorm.policy.FailFastCircuitBreaker;
import retrystorm.policy.RetryPolicy;
import retrystorm.scenario.Breakers;
import retrystorm.scenario.CanonicalExperiment;
import retrystorm.scenario.MultiSeedValidation;
import retrystorm.scenario.PhaseExperiment;
import retrystorm.scenario.Scenario;
import retrystorm.sim.Client;
import retrystorm.sim.ExponentialServiceTime;
import retrystorm.sim.RateSchedule;
import retrystorm.sim.Server;

/**
 * Measures how much of the baseline window each breaker kind spends tripped
 * (open or half-open) as baseline utilisation rises, with the same 100
 * independent clients as the phase map. Each breaker's state is sampled at a
 * fixed interval and averaged over all breakers and samples. Not part of the
 * canonical experiment; sampling only reads state and does not perturb the run.
 */
public final class BreakerStateAnalysis {

    private static final long SAMPLE_INTERVAL_MICROS = 10_000;
    private static final String HEADER = "breaker,utilisation,seed,baseline_tripped_fraction";

    private BreakerStateAnalysis() {
    }

    public static List<BreakerStateRow> run(Scenario base, List<Double> utilisations, List<Long> seeds) {
        double capacity = base.workers() * 1_000_000.0 / base.meanServiceMicros();

        List<BreakerStateRow> rows = new ArrayList<>();
        for (boolean failFast : new boolean[]{false, true}) {
            String name = failFast ? "fail-fast" : "retry-only";
            for (double utilisation : utilisations) {
                for (long seed : seeds) {
                    Scenario scenario = base.withSeed(seed)
                            .withArrivalRates(utilisation * capacity, base.spikeRatePerSecond());
                    rows.add(new BreakerStateRow(name, utilisation, seed,
                            baselineTrippedFraction(scenario, failFast)));
                }
            }
        }
        return rows;
    }

    public static void writeCsv(Path path, List<BreakerStateRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (BreakerStateRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%s,%.2f,%d,%.4f",
                        row.breaker(), row.utilisation(), row.seed(), row.baselineTrippedFraction()));
                writer.write('\n');
            }
        }
    }

    private static double baselineTrippedFraction(Scenario scenario, boolean failFast) {
        Simulator sim = new Simulator(scenario.seed());
        MetricsCollector metrics = new MetricsCollector(scenario.bucketMicros(), scenario.horizonMicros());
        Server server = new Server(sim, scenario.workers(), scenario.queueCapacity(),
                new ExponentialServiceTime(scenario.meanServiceMicros()));
        int clients = PhaseExperiment.HERD_CLIENTS;
        RateSchedule perClient = new RateSchedule(
                scenario.baseRatePerSecond() / clients, scenario.spikeRatePerSecond() / clients,
                scenario.spikeStartMicros(), scenario.spikeEndMicros());

        List<BooleanSupplier> tripped = new ArrayList<>(clients);
        for (int i = 0; i < clients; i++) {
            RetryPolicy policy;
            if (failFast) {
                FailFastCircuitBreaker breaker = Breakers.failFast();
                tripped.add(breaker::isTripped);
                policy = breaker;
            } else {
                CircuitBreaker breaker = Breakers.retryOnly();
                tripped.add(breaker::isTripped);
                policy = breaker;
            }
            new Client(sim, server, policy, metrics, perClient,
                    scenario.attemptTimeoutMicros(), scenario.maxAttempts()).start();
        }

        long[] counts = new long[2];
        sampleAt(sim, tripped, SAMPLE_INTERVAL_MICROS, scenario.spikeStartMicros(), counts);
        sim.run(scenario.spikeStartMicros());
        return counts[1] == 0 ? 0.0 : (double) counts[0] / counts[1];
    }

    private static void sampleAt(Simulator sim, List<BooleanSupplier> tripped, long at, long until, long[] counts) {
        if (at >= until) {
            return;
        }
        sim.schedule(at - sim.now(), () -> {
            for (BooleanSupplier probe : tripped) {
                counts[1]++;
                if (probe.getAsBoolean()) {
                    counts[0]++;
                }
            }
            sampleAt(sim, tripped, at + SAMPLE_INTERVAL_MICROS, until, counts);
        });
    }
}
