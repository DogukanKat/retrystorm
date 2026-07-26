package retrystorm.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;
import retrystorm.metrics.BucketRow;
import retrystorm.metrics.MetricsCollector;
import retrystorm.policy.FailureKind;
import retrystorm.policy.FixedRetry;
import retrystorm.policy.NoRetry;
import retrystorm.policy.RetryDecision;
import retrystorm.policy.RetryPolicy;

class ClientTest {

    private static final long MEAN_SERVICE_MICROS = 1_000;
    private static final long GENEROUS_TIMEOUT_MICROS = 10_000_000;
    private static final long TIGHT_TIMEOUT_MICROS = 1;
    private static final long OCCUPIED_FOREVER_MICROS = 1_000_000_000_000L;
    private static final long RETRY_DELAY_MICROS = 100_000;
    private static final RateSchedule IDLE = RateSchedule.constant(1);

    @Test
    void rejectsInvalidConfiguration() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 0);
        NoRetry policy = new NoRetry();
        MetricsCollector metrics = metrics();
        assertThrows(IllegalArgumentException.class,
                () -> new Client(sim, server, policy, metrics, IDLE, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Client(sim, server, policy, metrics, IDLE, 1_000, 0));
        assertThrows(NullPointerException.class,
                () -> new Client(sim, server, null, metrics, IDLE, 1_000, 1));
        assertThrows(NullPointerException.class,
                () -> new Client(sim, server, policy, null, IDLE, 1_000, 1));
        assertThrows(NullPointerException.class,
                () -> new Client(null, server, policy, metrics, IDLE, 1_000, 1));
    }

    @Test
    void servedRequestSettlesAsSuccess() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, server(sim, 1, 0), new NoRetry(), GENEROUS_TIMEOUT_MICROS, 3);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(Outcome.SUCCESS, request.outcome());
        assertEquals(1, request.attempts());
    }

    @Test
    void rejectedRequestSettlesAsRejected() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, server(sim, 1, 0), new NoRetry(), GENEROUS_TIMEOUT_MICROS, 3);
        client.send();
        Request rejected = client.send();
        assertEquals(Outcome.REJECTED, rejected.outcome());
        assertEquals(1, rejected.attempts());
    }

    @Test
    void timedOutRequestSettlesAsTimedOut() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, server(sim, 1, 0), new NoRetry(), TIGHT_TIMEOUT_MICROS, 3);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(Outcome.TIMED_OUT, request.outcome());
        assertEquals(1, request.attempts());
    }

    @Test
    void lateServerResponseCannotOverturnATimeout() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, server(sim, 1, 0), new NoRetry(), TIGHT_TIMEOUT_MICROS, 1);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(Outcome.TIMED_OUT, request.outcome());
    }

    @Test
    void timedOutAttemptStillOccupiesTheServer() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 0);
        Client client = client(sim, server, new NoRetry(), TIGHT_TIMEOUT_MICROS, 1);
        Request request = client.send();
        sim.run(TIGHT_TIMEOUT_MICROS);
        assertEquals(Outcome.TIMED_OUT, request.outcome());
        assertEquals(1, server.busyWorkers(), "abandoned work must still hold a worker");
    }

    @Test
    void retriesUntilTheRetryLimitThenKeepsTheLastFailureKind() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, fullServer(sim), new FixedRetry(2, 1_000), GENEROUS_TIMEOUT_MICROS, 10);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(3, request.attempts());
        assertEquals(Outcome.REJECTED, request.outcome());
    }

    @Test
    void attemptCapStopsRetriesEvenWhenThePolicyWouldContinue() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, fullServer(sim), new FixedRetry(100, 1_000), GENEROUS_TIMEOUT_MICROS, 4);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(4, request.attempts());
        assertEquals(Outcome.REJECTED, request.outcome());
    }

    @Test
    void retrySucceedsAfterAnEarlierRejection() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, brieflyBusyServer(sim), new FixedRetry(1, RETRY_DELAY_MICROS),
                GENEROUS_TIMEOUT_MICROS, 2);
        Request request = client.send();
        assertNull(request.outcome(), "a rejected attempt with a retry left must stay in flight");
        sim.runToCompletion();
        assertEquals(Outcome.SUCCESS, request.outcome());
        assertEquals(2, request.attempts());
    }

    @Test
    void terminalOutcomeReflectsTheLastFailureNotTheFirst() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, brieflyBusyServer(sim), new FixedRetry(1, RETRY_DELAY_MICROS),
                TIGHT_TIMEOUT_MICROS, 2);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(2, request.attempts());
        assertEquals(Outcome.TIMED_OUT, request.outcome(),
                "first attempt was rejected, last one timed out");
    }

    @Test
    void retryWaitsForThePolicyDelay() {
        long delay = 25_000;
        Simulator sim = new Simulator(1L);
        Client client = client(sim, fullServer(sim), new FixedRetry(1, delay), GENEROUS_TIMEOUT_MICROS, 5);
        Request request = client.send();
        assertEquals(1, request.attempts());
        sim.run(delay - 1);
        assertEquals(1, request.attempts(), "retry fired before the policy delay elapsed");
        sim.run(delay);
        assertEquals(2, request.attempts());
    }

    @Test
    void singleAttemptCapNeverRetries() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, fullServer(sim), new FixedRetry(5, 1_000), GENEROUS_TIMEOUT_MICROS, 1);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(1, request.attempts());
    }

    @Test
    void policySeesTheAttemptCountAndFailureKind() {
        Simulator sim = new Simulator(1L);
        RecordingPolicy policy = new RecordingPolicy(2);
        Client client = client(sim, fullServer(sim), policy, GENEROUS_TIMEOUT_MICROS, 10);
        client.send();
        sim.runToCompletion();
        assertEquals(List.of(1, 2, 3), policy.attempts);
        assertTrue(policy.kinds.stream().allMatch(kind -> kind == FailureKind.REJECTED));
    }

    @Test
    void retriedRequestKeepsItsOriginalCreationTime() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, fullServer(sim), new FixedRetry(2, 5_000), GENEROUS_TIMEOUT_MICROS, 10);
        sim.run(1_000);
        Request request = client.send();
        sim.runToCompletion();
        assertEquals(1_000, request.createdAtMicros());
        assertTrue(sim.now() > 1_000);
    }

    @Test
    void startGeneratesArrivalsAtRoughlyTheConfiguredRate() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, server(sim, 64, 1_024), new NoRetry(),
                GENEROUS_TIMEOUT_MICROS, 1, RateSchedule.constant(1_000));
        client.start();
        sim.run(10_000_000);
        long created = client.requestsCreated();
        assertTrue(created > 9_000 && created < 11_000, "arrivals in 10s at 1000/s was " + created);
    }

    @Test
    void arrivalsFollowTheSpikeWindow() {
        RateSchedule schedule = new RateSchedule(100, 2_000, 1_000_000, 2_000_000);
        Simulator sim = new Simulator(1L);
        Client client = client(sim, server(sim, 64, 1_024), new NoRetry(),
                GENEROUS_TIMEOUT_MICROS, 1, schedule);
        client.start();
        sim.run(1_000_000);
        long beforeSpike = client.requestsCreated();
        sim.run(2_000_000);
        long duringSpike = client.requestsCreated() - beforeSpike;
        assertTrue(duringSpike > beforeSpike * 5,
                "spike produced " + duringSpike + " vs baseline " + beforeSpike);
    }

    @Test
    void sameSeedProducesTheSameArrivalPattern() {
        assertEquals(arrivalCount(42L), arrivalCount(42L));
        assertTrue(arrivalCount(42L) != arrivalCount(43L));
    }

    @Test
    void notifiesThePolicyWhenARequestSucceeds() {
        Simulator sim = new Simulator(1L);
        CountingPolicy policy = new CountingPolicy();
        Client client = client(sim, server(sim, 1, 0), policy, GENEROUS_TIMEOUT_MICROS, 3);
        client.send();
        sim.runToCompletion();
        assertEquals(1, policy.successes);
        assertEquals(0, policy.failures);
    }

    @Test
    void notifiesThePolicyOnEveryFailedAttempt() {
        Simulator sim = new Simulator(1L);
        CountingPolicy policy = new CountingPolicy();
        Client client = client(sim, fullServer(sim), policy, GENEROUS_TIMEOUT_MICROS, 3);
        client.send();
        sim.runToCompletion();
        assertEquals(3, policy.failures, "one notification per attempt");
        assertEquals(0, policy.successes);
    }

    @Test
    void countsAFailureThenASuccessAcrossARetry() {
        Simulator sim = new Simulator(1L);
        CountingPolicy policy = new CountingPolicy();
        Client client = client(sim, brieflyBusyServer(sim), policy, GENEROUS_TIMEOUT_MICROS, 3);
        client.send();
        sim.runToCompletion();
        assertEquals(1, policy.failures);
        assertEquals(1, policy.successes);
    }

    @Test
    void recordsAnArrivalForEveryRequest() {
        Simulator sim = new Simulator(1L);
        MetricsCollector metrics = new MetricsCollector(1_000_000, 1_000_000);
        Client client = new Client(sim, server(sim, 1, 0), new NoRetry(), metrics, IDLE,
                GENEROUS_TIMEOUT_MICROS, 1);
        client.send();
        client.send();
        assertEquals(2, metrics.snapshot().get(0).offered());
    }

    @Test
    void recordsSuccessWithLatencyMeasuredFromCreation() {
        Simulator sim = new Simulator(1L);
        MetricsCollector metrics = new MetricsCollector(1_000_000, 1_000_000);
        Client client = new Client(sim, server(sim, 1, 0), new NoRetry(), metrics, IDLE,
                GENEROUS_TIMEOUT_MICROS, 1);
        client.send();
        sim.runToCompletion();
        BucketRow row = metrics.snapshot().get(0);
        assertEquals(1, row.goodput());
        assertTrue(row.p50LatencyMicros() > 0, "a served request has positive latency");
    }

    @Test
    void recordsRejectionsTimeoutsAndRetries() {
        Simulator sim = new Simulator(1L);
        MetricsCollector metrics = new MetricsCollector(1_000_000, 1_000_000_000);
        Client client = new Client(sim, fullServer(sim), new FixedRetry(2, RETRY_DELAY_MICROS), metrics,
                IDLE, GENEROUS_TIMEOUT_MICROS, 3);
        client.send();
        sim.runToCompletion();
        List<BucketRow> rows = metrics.snapshot();
        long rejections = rows.stream().mapToLong(BucketRow::rejections).sum();
        long retries = rows.stream().mapToLong(BucketRow::retries).sum();
        assertEquals(3, rejections, "three attempts, all rejected");
        assertEquals(2, retries, "two of them were retries");
    }

    @Test
    void recordsATimeoutForAnAcceptedButUnservedAttempt() {
        Simulator sim = new Simulator(1L);
        MetricsCollector metrics = new MetricsCollector(1_000_000, 1_000_000);
        Server server = new Server(sim, 1, 1, new ExponentialServiceTime(OCCUPIED_FOREVER_MICROS));
        server.submit(() -> {
        });
        Client client = new Client(sim, server, new NoRetry(), metrics, IDLE, TIGHT_TIMEOUT_MICROS, 1);
        client.send();
        sim.run(TIGHT_TIMEOUT_MICROS);
        long timeouts = metrics.snapshot().stream().mapToLong(BucketRow::timeouts).sum();
        assertEquals(1, timeouts);
    }

    @Test
    void requestIsUnsettledWhileInFlight() {
        Simulator sim = new Simulator(1L);
        Client client = client(sim, server(sim, 1, 0), new NoRetry(), GENEROUS_TIMEOUT_MICROS, 1);
        Request request = client.send();
        assertNull(request.outcome());
    }

    private static long arrivalCount(long seed) {
        Simulator sim = new Simulator(seed);
        Client client = client(sim, server(sim, 64, 1_024), new NoRetry(),
                GENEROUS_TIMEOUT_MICROS, 1, RateSchedule.constant(500));
        client.start();
        sim.run(5_000_000);
        return client.requestsCreated();
    }

    private static Server server(Simulator sim, int workers, int queueCapacity) {
        return new Server(sim, workers, queueCapacity, new ExponentialServiceTime(MEAN_SERVICE_MICROS));
    }

    /**
     * A server whose only worker is busy for far longer than any test runs, so
     * every later submission is rejected.
     */
    private static Server fullServer(Simulator sim) {
        Server server = new Server(sim, 1, 0, new ExponentialServiceTime(OCCUPIED_FOREVER_MICROS));
        server.submit(() -> {
        });
        return server;
    }

    /**
     * A server busy with short work, so the first submission is rejected but a
     * retry sent after {@link #RETRY_DELAY_MICROS} finds the worker free.
     */
    private static Server brieflyBusyServer(Simulator sim) {
        Server server = new Server(sim, 1, 0, new ExponentialServiceTime(MEAN_SERVICE_MICROS));
        server.submit(() -> {
        });
        return server;
    }

    private static Client client(Simulator sim, Server server, RetryPolicy policy,
                                 long timeoutMicros, int maxAttempts) {
        return client(sim, server, policy, timeoutMicros, maxAttempts, IDLE);
    }

    private static Client client(Simulator sim, Server server, RetryPolicy policy,
                                 long timeoutMicros, int maxAttempts, RateSchedule schedule) {
        return new Client(sim, server, policy, metrics(), schedule, timeoutMicros, maxAttempts);
    }

    private static MetricsCollector metrics() {
        return new MetricsCollector(1_000_000, 1_000_000_000);
    }

    private static final class CountingPolicy implements RetryPolicy {

        private int successes;
        private int failures;

        @Override
        public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
            return RetryDecision.retryAfter(RETRY_DELAY_MICROS);
        }

        @Override
        public void onSuccess() {
            successes++;
        }

        @Override
        public void onFailure() {
            failures++;
        }
    }

    private static final class RecordingPolicy implements RetryPolicy {

        private final List<Integer> attempts = new ArrayList<>();
        private final List<FailureKind> kinds = new ArrayList<>();
        private final int maxRetries;

        private RecordingPolicy(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
            attempts.add(attempt);
            kinds.add(failureKind);
            return attempt <= maxRetries ? RetryDecision.retryAfter(1_000) : RetryDecision.giveUp();
        }
    }
}
