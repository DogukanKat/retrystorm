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
import retrystorm.policy.FailFastCircuitBreaker;
import retrystorm.scenario.Breakers;
import retrystorm.scenario.PhaseExperiment;
import retrystorm.scenario.Scenario;
import retrystorm.sim.Client;
import retrystorm.sim.ExponentialServiceTime;
import retrystorm.sim.RateSchedule;
import retrystorm.sim.Server;

/**
 * Records how many of the 100 independent fail-fast breakers are open over time,
 * sampled every 100 ms. A recovery-window trace that swings in waves is evidence
 * of synchronized trip-and-probe cycles; one that hovers around a steady mean is
 * independent flapping. Not part of the canonical experiment; sampling only reads
 * state and does not perturb the run.
 */
public final class OpenTraceAnalysis {

    private static final long SAMPLE_INTERVAL_MICROS = 100_000;
    private static final String HEADER = "seed,time_s,open_count";

    private OpenTraceAnalysis() {
    }

    public static List<OpenTraceRow> run(Scenario base, List<Long> seeds) {
        List<OpenTraceRow> rows = new ArrayList<>();
        for (long seed : seeds) {
            trace(base.withSeed(seed), rows);
        }
        return rows;
    }

    public static void writeCsv(Path path, List<OpenTraceRow> rows) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(HEADER);
            writer.write('\n');
            for (OpenTraceRow row : rows) {
                writer.write(String.format(Locale.ROOT, "%d,%.3f,%d",
                        row.seed(), row.timeSeconds(), row.openCount()));
                writer.write('\n');
            }
        }
    }

    private static void trace(Scenario scenario, List<OpenTraceRow> out) {
        Simulator sim = new Simulator(scenario.seed());
        MetricsCollector metrics = new MetricsCollector(scenario.bucketMicros(), scenario.horizonMicros());
        Server server = new Server(sim, scenario.workers(), scenario.queueCapacity(),
                new ExponentialServiceTime(scenario.meanServiceMicros()));
        int clients = PhaseExperiment.HERD_CLIENTS;
        RateSchedule perClient = new RateSchedule(
                scenario.baseRatePerSecond() / clients, scenario.spikeRatePerSecond() / clients,
                scenario.spikeStartMicros(), scenario.spikeEndMicros());

        List<BooleanSupplier> open = new ArrayList<>(clients);
        for (int i = 0; i < clients; i++) {
            FailFastCircuitBreaker breaker = Breakers.failFast();
            open.add(breaker::isTripped);
            new Client(sim, server, breaker, metrics, perClient,
                    scenario.attemptTimeoutMicros(), scenario.maxAttempts()).start();
        }
        sampleAt(sim, open, SAMPLE_INTERVAL_MICROS, scenario.horizonMicros(), scenario.seed(), out);
        sim.run(scenario.horizonMicros());
    }

    private static void sampleAt(Simulator sim, List<BooleanSupplier> open, long at, long until,
                                 long seed, List<OpenTraceRow> out) {
        if (at >= until) {
            return;
        }
        sim.schedule(at - sim.now(), () -> {
            int count = 0;
            for (BooleanSupplier probe : open) {
                if (probe.getAsBoolean()) {
                    count++;
                }
            }
            out.add(new OpenTraceRow(seed, at / 1_000_000.0, count));
            sampleAt(sim, open, at + SAMPLE_INTERVAL_MICROS, until, seed, out);
        });
    }
}
