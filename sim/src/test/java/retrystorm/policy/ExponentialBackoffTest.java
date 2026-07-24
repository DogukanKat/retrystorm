package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class ExponentialBackoffTest {

    private final Simulator sim = new Simulator(1L);

    @Test
    void firstRetryWaitsTheBaseDelay() {
        ExponentialBackoff policy = new ExponentialBackoff(10, 1_000, 1_000_000);
        assertEquals(1_000L, policy.decide(1, FailureKind.TIMED_OUT, sim).delayMicros());
    }

    @Test
    void delayDoublesOnEveryAttempt() {
        ExponentialBackoff policy = new ExponentialBackoff(10, 1_000, 1_000_000);
        assertEquals(2_000L, policy.decide(2, FailureKind.TIMED_OUT, sim).delayMicros());
        assertEquals(4_000L, policy.decide(3, FailureKind.TIMED_OUT, sim).delayMicros());
        assertEquals(8_000L, policy.decide(4, FailureKind.TIMED_OUT, sim).delayMicros());
    }

    @Test
    void delayStopsAtTheCeiling() {
        ExponentialBackoff policy = new ExponentialBackoff(20, 1_000, 5_000);
        assertEquals(4_000L, policy.decide(3, FailureKind.TIMED_OUT, sim).delayMicros());
        assertEquals(5_000L, policy.decide(4, FailureKind.TIMED_OUT, sim).delayMicros());
        assertEquals(5_000L, policy.decide(15, FailureKind.TIMED_OUT, sim).delayMicros());
    }

    @Test
    void ceilingEqualToBaseNeverGrows() {
        ExponentialBackoff policy = new ExponentialBackoff(5, 1_000, 1_000);
        assertEquals(1_000L, policy.decide(1, FailureKind.TIMED_OUT, sim).delayMicros());
        assertEquals(1_000L, policy.decide(4, FailureKind.TIMED_OUT, sim).delayMicros());
    }

    @Test
    void hugeCeilingDoesNotOverflow() {
        ExponentialBackoff policy = new ExponentialBackoff(200, 1_000, Long.MAX_VALUE);
        for (int attempt = 1; attempt <= 200; attempt++) {
            long delay = policy.decide(attempt, FailureKind.TIMED_OUT, sim).delayMicros();
            assertTrue(delay > 0, "attempt " + attempt + " produced " + delay);
        }
    }

    @Test
    void givesUpPastTheRetryLimit() {
        ExponentialBackoff policy = new ExponentialBackoff(2, 1_000, 1_000_000);
        assertTrue(policy.decide(2, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(policy.decide(3, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void treatsBothFailureKindsAlike() {
        ExponentialBackoff policy = new ExponentialBackoff(5, 1_000, 1_000_000);
        assertEquals(policy.decide(2, FailureKind.REJECTED, sim),
                policy.decide(2, FailureKind.TIMED_OUT, sim));
    }

    @Test
    void drawsNoRandomness() {
        Simulator untouched = new Simulator(99L);
        long expected = new Simulator(99L).random().nextLong();
        new ExponentialBackoff(5, 1_000, 1_000_000).decide(2, FailureKind.TIMED_OUT, untouched);
        assertEquals(expected, untouched.random().nextLong());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoff(-1, 1_000, 2_000));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoff(1, 0, 2_000));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialBackoff(1, 2_000, 1_000));
    }
}
