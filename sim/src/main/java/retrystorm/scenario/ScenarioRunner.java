package retrystorm.scenario;

import java.util.List;

import retrystorm.engine.Simulator;
import retrystorm.metrics.BucketRow;
import retrystorm.metrics.MetricsCollector;
import retrystorm.policy.RetryPolicy;
import retrystorm.sim.Client;
import retrystorm.sim.ExponentialServiceTime;
import retrystorm.sim.Server;

/** Composes a client, server and policy into one run and returns its metric rows. */
public final class ScenarioRunner {

    private ScenarioRunner() {
    }

    public static List<BucketRow> run(Scenario scenario, RetryPolicy policy) {
        Simulator sim = new Simulator(scenario.seed());
        MetricsCollector metrics = new MetricsCollector(scenario.bucketMicros(), scenario.horizonMicros());
        Server server = new Server(sim, scenario.workers(), scenario.queueCapacity(),
                new ExponentialServiceTime(scenario.meanServiceMicros()));
        Client client = new Client(sim, server, policy, metrics, scenario.rateSchedule(),
                scenario.attemptTimeoutMicros(), scenario.maxAttempts());

        sampleQueueAtBucketEdge(sim, metrics, server, scenario.bucketMicros(),
                scenario.horizonMicros(), 1, metrics.bucketCount());
        client.start();
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
