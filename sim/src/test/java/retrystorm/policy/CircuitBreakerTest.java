package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class CircuitBreakerTest {

    private static final long DELAY = 1_000;
    private static final long COOLDOWN = 10_000;

    private final Simulator sim = new Simulator(1L);

    private CircuitBreaker breaker(int windowSize, double threshold) {
        return new CircuitBreaker(100, DELAY, windowSize, threshold, COOLDOWN);
    }

    private static void feedFailures(CircuitBreaker breaker, int count) {
        for (int i = 0; i < count; i++) {
            breaker.onFailure();
        }
    }

    @Test
    void staysClosedWhileFailuresAreBelowTheThreshold() {
        CircuitBreaker breaker = breaker(4, 0.75);
        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onSuccess();
        assertTrue(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void doesNotTripUntilTheWindowIsFull() {
        CircuitBreaker breaker = breaker(4, 0.5);
        breaker.onFailure();
        breaker.onFailure();
        assertTrue(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(),
                "two failures in a window of four is not yet a full window");
    }

    @Test
    void tripsOpenWhenTheFailureRateReachesTheThreshold() {
        CircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        assertFalse(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void staysOpenUntilTheCooldownElapses() {
        CircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        sim.run(COOLDOWN - 1);
        assertFalse(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void halfOpensAfterTheCooldownAndAllowsOneProbe() {
        CircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        sim.run(COOLDOWN);
        assertTrue(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(), "cooldown elapsed, one probe allowed");
        assertFalse(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(), "only one probe at a time");
    }

    @Test
    void closesWhenTheProbeSucceeds() {
        CircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        sim.run(COOLDOWN);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        breaker.onSuccess();
        assertTrue(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(), "a successful probe closes the breaker");
    }

    @Test
    void reopensWhenTheProbeFails() {
        CircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        sim.run(COOLDOWN);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        breaker.onFailure();
        assertFalse(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(), "a failed probe reopens the breaker");
    }

    @Test
    void allowsANewProbeAfterReopeningAndWaitingAgain() {
        CircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        sim.run(COOLDOWN);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        breaker.onFailure();
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        sim.run(2 * COOLDOWN);
        assertTrue(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(),
                "reopening restarts the cooldown, after which a fresh probe is allowed");
    }

    @Test
    void tripsWhenTheFailureRateExactlyEqualsTheThreshold() {
        CircuitBreaker breaker = breaker(4, 0.5);
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();
        breaker.onSuccess();
        assertFalse(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(),
                "a failure rate equal to the threshold must trip the breaker");
    }

    @Test
    void closedBreakerRespectsTheAttemptCap() {
        CircuitBreaker breaker = new CircuitBreaker(2, DELAY, 4, 0.75, COOLDOWN);
        assertTrue(breaker.decide(2, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(breaker.decide(3, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void closingResetsTheFailureWindow() {
        CircuitBreaker breaker = breaker(4, 0.75);
        feedFailures(breaker, 4);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        sim.run(COOLDOWN);
        breaker.decide(1, FailureKind.TIMED_OUT, sim);
        breaker.onSuccess();
        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();
        assertTrue(breaker.decide(1, FailureKind.TIMED_OUT, sim).retry(),
                "three failures cannot fill a window of four after a reset");
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(-1, DELAY, 4, 0.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(1, -1, 4, 0.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(1, DELAY, 0, 0.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(1, DELAY, 4, 0, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(1, DELAY, 4, 1.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(1, DELAY, 4, 0.5, 0));
    }
}
