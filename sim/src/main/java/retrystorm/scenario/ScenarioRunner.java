package retrystorm.scenario;

import java.util.List;
import java.util.function.Supplier;

import retrystorm.engine.Simulator;
import retrystorm.metrics.BucketRow;
import retrystorm.metrics.MetricsCollector;
import retrystorm.policy.RetryPolicy;
import retrystorm.sim.Client;
import retrystorm.sim.ExponentialServiceTime;
import retrystorm.sim.RateSchedule;
import retrystorm.sim.Server;

/** Composes clients, a server and a policy into one run and returns its metric rows. */
public final class ScenarioRunner {

    private ScenarioRunner() {
    }

    public static List<BucketRow> run(Scenario scenario, RetryPolicy policy) {
        return runWithClients(scenario, () -> policy, 1);
    }

    /**
     * Runs the scenario with {@code clientCount} independent clients that share
     * the total offered load and each own a fresh policy instance. With a
     * per-client policy such as a circuit breaker, each client sees only its
     * own outcomes, so the perfect coordination of a single shared policy is
     * gone.
     */
    public static List<BucketRow> runWithClients(Scenario scenario, Supplier<RetryPolicy> policyFactory,
                                                 int clientCount) {
        if (clientCount <= 0) {
            throw new IllegalArgumentException("clientCount must be positive: " + clientCount);
        }
        Simulator sim = new Simulator(scenario.seed());
        MetricsCollector metrics = new MetricsCollector(scenario.bucketMicros(), scenario.horizonMicros());
        Server server = new Server(sim, scenario.workers(), scenario.queueCapacity(),
                new ExponentialServiceTime(scenario.meanServiceMicros()));
        RateSchedule perClient = new RateSchedule(
                scenario.baseRatePerSecond() / clientCount,
                scenario.spikeRatePerSecond() / clientCount,
                scenario.spikeStartMicros(), scenario.spikeEndMicros());

        for (int i = 0; i < clientCount; i++) {
            new Client(sim, server, policyFactory.get(), metrics, perClient,
                    scenario.attemptTimeoutMicros(), scenario.maxAttempts()).start();
        }
        sampleQueueAtBucketEdge(sim, metrics, server, scenario.bucketMicros(),
                scenario.horizonMicros(), 1, metrics.bucketCount());
        sim.run(scenario.horizonMicros());
        return metrics.snapshot();
    }

    private static void sampleQueueAtBucketEdge(Simulator sim, MetricsCollector metrics, Server server,
                                                long bucketMicros, long horizonMicros, int edgeIndex, int bucketCount) {
        if (edgeIndex > bucketCount) {
            return;
        }
        // The final bucket may be narrower than bucketMicros; sample it at the
        // horizon so every bucket, including a partial last one, is covered.
        long edgeMicros = Math.min((long) edgeIndex * bucketMicros, horizonMicros);
        sim.schedule(edgeMicros - sim.now(), () -> {
            metrics.recordQueueDepth(edgeIndex - 1, server.queueDepth());
            sampleQueueAtBucketEdge(sim, metrics, server, bucketMicros, horizonMicros, edgeIndex + 1, bucketCount);
        });
    }
}
