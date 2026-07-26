package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class FailFastCircuitBreakerTest {

    private static final long DELAY = 1_000;
    private static final long COOLDOWN = 10_000;

    private final Simulator sim = new Simulator(1L);

    private FailFastCircuitBreaker breaker(int windowSize, double threshold) {
        return new FailFastCircuitBreaker(100, DELAY, windowSize, threshold, COOLDOWN);
    }

    private static void feedFailures(FailFastCircuitBreaker breaker, int count) {
        for (int i = 0; i < count; i++) {
            breaker.onFailure();
        }
    }

    @Test
    void closedAdmitsRequestsAndAllowsRetries() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        assertTrue(breaker.admit(sim));
        assertTrue(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void doesNotTripUntilTheWindowIsFull() {
        FailFastCircuitBreaker breaker = breaker(4, 0.5);
        breaker.onFailure();
        breaker.onFailure();
        assertTrue(breaker.admit(sim));
    }

    @Test
    void staysClosedBelowTheThreshold() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onSuccess();
        assertTrue(breaker.admit(sim));
    }

    @Test
    void openStopsAdmittingRequestsEntirely() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        assertFalse(breaker.admit(sim));
        assertFalse(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void staysOpenUntilTheCooldownElapses() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.admit(sim);
        sim.run(COOLDOWN - 1);
        assertFalse(breaker.admit(sim));
    }

    @Test
    void halfOpensAfterCooldownAndAdmitsExactlyOneProbe() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.admit(sim);
        sim.run(COOLDOWN);
        assertTrue(breaker.admit(sim), "one probe admitted after cooldown");
        assertFalse(breaker.admit(sim), "no second probe while the first is in flight");
    }

    @Test
    void probeSuccessClosesTheBreaker() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.admit(sim);
        sim.run(COOLDOWN);
        breaker.admit(sim);
        breaker.onSuccess();
        assertTrue(breaker.admit(sim), "a successful probe reopens the gate");
    }

    @Test
    void probeFailureReopensTheBreaker() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.admit(sim);
        sim.run(COOLDOWN);
        breaker.admit(sim);
        breaker.onFailure();
        assertFalse(breaker.admit(sim), "a failed probe shuts the gate again");
    }

    @Test
    void reopenedBreakerAdmitsAnotherProbeOnlyAfterAFreshCooldown() {
        FailFastCircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.admit(sim);
        sim.run(COOLDOWN);
        breaker.admit(sim);
        breaker.onFailure();
        breaker.admit(sim);
        sim.run(2 * COOLDOWN);
        assertTrue(breaker.admit(sim), "after a fresh cooldown a new probe is allowed");
    }

    @Test
    void closedBreakerRespectsTheAttemptCap() {
        FailFastCircuitBreaker breaker = new FailFastCircuitBreaker(2, DELAY, 4, 0.75, COOLDOWN);
        assertTrue(breaker.decide(2, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(breaker.decide(3, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new FailFastCircuitBreaker(-1, DELAY, 4, 0.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new FailFastCircuitBreaker(1, -1, 4, 0.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new FailFastCircuitBreaker(1, DELAY, 0, 0.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new FailFastCircuitBreaker(1, DELAY, 4, 0, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new FailFastCircuitBreaker(1, DELAY, 4, 1.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new FailFastCircuitBreaker(1, DELAY, 4, 0.5, 0));
    }
}
