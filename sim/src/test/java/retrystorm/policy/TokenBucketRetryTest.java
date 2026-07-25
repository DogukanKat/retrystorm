package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class TokenBucketRetryTest {

    private final Simulator sim = new Simulator(1L);

    private static TokenBucketRetry bucket(double capacity, double cost, double refill) {
        return new TokenBucketRetry(100, 1_000, capacity, cost, refill);
    }

    @Test
    void retriesWhileTokensRemain() {
        TokenBucketRetry policy = bucket(3, 1, 0.1);
        assertTrue(policy.decide(1, FailureKind.TIMED_OUT, sim).retry());
        assertTrue(policy.decide(2, FailureKind.TIMED_OUT, sim).retry());
        assertTrue(policy.decide(3, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void deniesRetryOnceTheBudgetIsEmpty() {
        TokenBucketRetry policy = bucket(3, 1, 0.1);
        policy.decide(1, FailureKind.TIMED_OUT, sim);
        policy.decide(2, FailureKind.TIMED_OUT, sim);
        policy.decide(3, FailureKind.TIMED_OUT, sim);
        assertFalse(policy.decide(4, FailureKind.TIMED_OUT, sim).retry(),
                "an empty bucket must stop retrying even under the attempt cap");
    }

    @Test
    void successRefillsTheBudget() {
        TokenBucketRetry policy = bucket(2, 1, 1);
        policy.decide(1, FailureKind.TIMED_OUT, sim);
        policy.decide(2, FailureKind.TIMED_OUT, sim);
        assertFalse(policy.decide(3, FailureKind.TIMED_OUT, sim).retry());
        policy.onSuccess();
        assertTrue(policy.decide(4, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void refillNeverExceedsCapacity() {
        TokenBucketRetry policy = bucket(2, 1, 1);
        policy.onSuccess();
        policy.onSuccess();
        policy.onSuccess();
        assertTrue(policy.decide(1, FailureKind.TIMED_OUT, sim).retry());
        assertTrue(policy.decide(2, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(policy.decide(3, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void partialRefillCannotPayForARetryUntilItAddsUp() {
        TokenBucketRetry policy = bucket(1, 1, 0.3);
        assertTrue(policy.decide(1, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(policy.decide(2, FailureKind.TIMED_OUT, sim).retry());
        policy.onSuccess();
        policy.onSuccess();
        assertFalse(policy.decide(3, FailureKind.TIMED_OUT, sim).retry(), "0.6 tokens cannot pay a cost of 1");
        policy.onSuccess();
        policy.onSuccess();
        assertTrue(policy.decide(4, FailureKind.TIMED_OUT, sim).retry(), "1.2 tokens can pay a cost of 1");
    }

    @Test
    void accumulatedRefillsThatSumToTheCostStillPay() {
        TokenBucketRetry policy = bucket(5, 1, 0.1);
        for (int attempt = 1; attempt <= 5; attempt++) {
            policy.decide(attempt, FailureKind.TIMED_OUT, sim);
        }
        for (int refill = 0; refill < 10; refill++) {
            policy.onSuccess();
        }
        assertTrue(policy.decide(6, FailureKind.TIMED_OUT, sim).retry(),
                "ten 0.1 refills fund one retry despite floating-point drift");
    }

    @Test
    void aBudgetShortByMoreThanRoundingStillDenies() {
        TokenBucketRetry policy = bucket(1, 1, 0.3);
        assertTrue(policy.decide(1, FailureKind.TIMED_OUT, sim).retry());
        policy.onSuccess();
        policy.onSuccess();
        assertFalse(policy.decide(2, FailureKind.TIMED_OUT, sim).retry(),
                "0.6 tokens is genuinely short, not a rounding artifact");
    }

    @Test
    void attemptCapStillApplies() {
        TokenBucketRetry policy = new TokenBucketRetry(1, 1_000, 100, 1, 1);
        assertTrue(policy.decide(1, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(policy.decide(2, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void retryCarriesTheConfiguredDelay() {
        TokenBucketRetry policy = new TokenBucketRetry(5, 4_200, 10, 1, 1);
        assertEquals(4_200L, policy.decide(1, FailureKind.TIMED_OUT, sim).delayMicros());
    }

    @Test
    void spendingIsDeterministic() {
        assertEquals(retriesAllowed(11L), retriesAllowed(11L));
    }

    private static int retriesAllowed(long seed) {
        Simulator sim = new Simulator(seed);
        TokenBucketRetry policy = new TokenBucketRetry(100, 1_000, 5, 1, 0.5);
        int allowed = 0;
        for (int attempt = 1; attempt <= 20; attempt++) {
            if (attempt % 3 == 0) {
                policy.onSuccess();
            }
            if (policy.decide(attempt, FailureKind.TIMED_OUT, sim).retry()) {
                allowed++;
            }
        }
        return allowed;
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRetry(-1, 1_000, 5, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRetry(1, -1, 5, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRetry(1, 1_000, 0, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRetry(1, 1_000, 5, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRetry(1, 1_000, 5, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRetry(1, 1_000, 1, 2, 0.5));
    }
}
