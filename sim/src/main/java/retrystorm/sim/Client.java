package retrystorm.sim;

import java.util.Objects;

import retrystorm.engine.Simulator;
import retrystorm.policy.FailureKind;
import retrystorm.policy.RetryDecision;
import retrystorm.policy.RetryPolicy;

/**
 * An open workload. Arrivals are a Poisson process at the scheduled rate, and
 * every attempt carries its own timeout. When an attempt fails the retry
 * policy decides what happens next, bounded by a hard per-request attempt cap.
 *
 * <p>A request that never succeeds is settled with the failure kind of its
 * final attempt, so {@link Outcome#REJECTED} and {@link Outcome#TIMED_OUT}
 * say how it ended and {@link Request#attempts()} says how hard it tried.
 */
public final class Client {

    private final Simulator sim;
    private final Server server;
    private final RetryPolicy policy;
    private final RateSchedule rateSchedule;
    private final long attemptTimeoutMicros;
    private final int maxAttempts;
    private long requestsCreated;

    public Client(Simulator sim, Server server, RetryPolicy policy, RateSchedule rateSchedule,
                  long attemptTimeoutMicros, int maxAttempts) {
        if (attemptTimeoutMicros <= 0) {
            throw new IllegalArgumentException("attemptTimeoutMicros must be positive: " + attemptTimeoutMicros);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive: " + maxAttempts);
        }
        this.sim = Objects.requireNonNull(sim, "sim");
        this.server = Objects.requireNonNull(server, "server");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.rateSchedule = Objects.requireNonNull(rateSchedule, "rateSchedule");
        this.attemptTimeoutMicros = attemptTimeoutMicros;
        this.maxAttempts = maxAttempts;
    }

    /** Starts the arrival process. Arrivals continue until the run reaches its horizon. */
    public void start() {
        scheduleNextArrival();
    }

    long requestsCreated() {
        return requestsCreated;
    }

    Request send() {
        Request request = new Request(requestsCreated++, sim.now());
        sendAttempt(request);
        return request;
    }

    private void scheduleNextArrival() {
        sim.schedule(nextGapMicros(), () -> {
            send();
            scheduleNextArrival();
        });
    }

    private void sendAttempt(Request request) {
        request.recordAttempt();
        Attempt attempt = new Attempt();
        if (!server.submit(() -> onServed(request, attempt))) {
            handleFailure(request, FailureKind.REJECTED);
            return;
        }
        sim.schedule(attemptTimeoutMicros, () -> onTimeout(request, attempt));
    }

    private void onServed(Request request, Attempt attempt) {
        if (attempt.resolve()) {
            request.settle(Outcome.SUCCESS);
        }
    }

    private void onTimeout(Request request, Attempt attempt) {
        if (attempt.resolve()) {
            handleFailure(request, FailureKind.TIMED_OUT);
        }
    }

    private void handleFailure(Request request, FailureKind failureKind) {
        if (request.attempts() >= maxAttempts) {
            request.settle(terminalOutcome(failureKind));
            return;
        }
        RetryDecision decision = policy.decide(request.attempts(), failureKind, sim);
        if (!decision.retry()) {
            request.settle(terminalOutcome(failureKind));
            return;
        }
        sim.schedule(decision.delayMicros(), () -> sendAttempt(request));
    }

    private static Outcome terminalOutcome(FailureKind failureKind) {
        return failureKind == FailureKind.REJECTED ? Outcome.REJECTED : Outcome.TIMED_OUT;
    }

    private long nextGapMicros() {
        double meanGapMicros = 1_000_000.0 / rateSchedule.ratePerSecondAt(sim.now());
        double gap = -meanGapMicros * Math.log(1.0 - sim.random().nextDouble());
        return Math.max(1L, Math.round(gap));
    }
}
