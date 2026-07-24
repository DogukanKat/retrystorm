package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class FixedRetryTest {

    private final Simulator sim = new Simulator(1L);

    @Test
    void retriesWhileUnderTheRetryLimit() {
        FixedRetry policy = new FixedRetry(2, 5_000);
        assertTrue(policy.decide(1, FailureKind.TIMED_OUT, sim).retry());
        assertTrue(policy.decide(2, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void givesUpOnceTheRetryLimitIsReached() {
        FixedRetry policy = new FixedRetry(2, 5_000);
        assertFalse(policy.decide(3, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(policy.decide(4, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void delayIsTheSameOnEveryRetry() {
        FixedRetry policy = new FixedRetry(4, 7_500);
        assertEquals(7_500L, policy.decide(1, FailureKind.TIMED_OUT, sim).delayMicros());
        assertEquals(7_500L, policy.decide(2, FailureKind.TIMED_OUT, sim).delayMicros());
        assertEquals(7_500L, policy.decide(4, FailureKind.TIMED_OUT, sim).delayMicros());
    }

    @Test
    void zeroRetriesBehavesLikeNoRetry() {
        FixedRetry policy = new FixedRetry(0, 1_000);
        assertFalse(policy.decide(1, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void treatsBothFailureKindsAlike() {
        FixedRetry policy = new FixedRetry(1, 1_000);
        assertEquals(policy.decide(1, FailureKind.REJECTED, sim),
                policy.decide(1, FailureKind.TIMED_OUT, sim));
    }

    @Test
    void allowsZeroDelay() {
        assertEquals(0L, new FixedRetry(1, 0).decide(1, FailureKind.REJECTED, sim).delayMicros());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRetry(-1, 1_000));
        assertThrows(IllegalArgumentException.class, () -> new FixedRetry(1, -1));
    }
}
