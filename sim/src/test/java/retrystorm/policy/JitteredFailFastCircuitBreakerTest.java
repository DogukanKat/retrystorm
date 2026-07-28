package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class JitteredFailFastCircuitBreakerTest {

    private static final long DELAY = 1_000;
    private static final long COOLDOWN = 10_000;

    private final Simulator sim = new Simulator(1L);

    private JitteredFailFastCircuitBreaker breaker() {
        return new JitteredFailFastCircuitBreaker(100, DELAY, 4, 0.75, COOLDOWN);
    }

    private static void feedFailures(JitteredFailFastCircuitBreaker breaker, int count) {
        for (int i = 0; i < count; i++) {
            breaker.onFailure();
        }
    }

    @Test
    void closedAdmitsRequests() {
        assertTrue(breaker().admit(sim));
    }

    @Test
    void tripsOpenAndStaysOpenForAtLeastTheBaseCooldown() {
        JitteredFailFastCircuitBreaker breaker = breaker();
        feedFailures(breaker, 4);
        assertFalse(breaker.admit(sim), "a full failure window trips it open");
        sim.run(COOLDOWN - 1);
        assertFalse(breaker.admit(sim), "effective cooldown is never shorter than the base cooldown");
    }

    @Test
    void halfOpensByTwiceTheBaseCooldownAndAdmitsOneProbe() {
        JitteredFailFastCircuitBreaker breaker = breaker();
        feedFailures(breaker, 4);
        breaker.admit(sim);
        sim.run(2 * COOLDOWN);
        assertTrue(breaker.admit(sim), "effective cooldown never exceeds twice the base, so a probe is admitted");
        assertFalse(breaker.admit(sim), "only one probe while half-open");
    }

    @Test
    void probeSuccessClosesAndProbeFailureReopens() {
        JitteredFailFastCircuitBreaker closing = breaker();
        feedFailures(closing, 4);
        closing.admit(sim);
        sim.run(2 * COOLDOWN);
        closing.admit(sim);
        closing.onSuccess();
        assertTrue(closing.admit(sim), "a successful probe closes it");

        Simulator sim2 = new Simulator(1L);
        JitteredFailFastCircuitBreaker reopening =
                new JitteredFailFastCircuitBreaker(100, DELAY, 4, 0.75, COOLDOWN);
        feedFailures(reopening, 4);
        reopening.admit(sim2);
        sim2.run(2 * COOLDOWN);
        reopening.admit(sim2);
        reopening.onFailure();
        assertFalse(reopening.admit(sim2), "a failed probe reopens it");
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new JitteredFailFastCircuitBreaker(1, DELAY, 4, 0.5, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new JitteredFailFastCircuitBreaker(1, DELAY, 4, 0.5, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> new JitteredFailFastCircuitBreaker(1, DELAY, 0, 0.5, COOLDOWN));
        assertThrows(IllegalArgumentException.class,
                () -> new JitteredFailFastCircuitBreaker(1, DELAY, 4, 1.5, COOLDOWN));
    }
}
