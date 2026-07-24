package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class ExponentialBackoffWithJitterTest {

    @Test
    void neverExceedsTheExponentialCap() {
        ExponentialBackoffWithJitter policy = new ExponentialBackoffWithJitter(10, 1_000, 100_000);
        Simulator sim = new Simulator(5L);
        long[] caps = {1_000, 2_000, 4_000, 8_000, 16_000, 32_000, 64_000, 100_000, 100_000, 100_000};
        for (int attempt = 1; attempt <= caps.length; attempt++) {
            for (int draw = 0; draw < 200; draw++) {
                long delay = policy.decide(attempt, FailureKind.TIMED_OUT, sim).delayMicros();
                assertTrue(delay >= 0 && delay <= caps[attempt - 1],
                        "attempt " + attempt + " drew " + delay + " against cap " + caps[attempt - 1]);
            }
        }
    }

    @Test
    void spreadsRetriesInsteadOfReturningTheCap() {
        ExponentialBackoffWithJitter policy = new ExponentialBackoffWithJitter(10, 1_000, 100_000);
        Simulator sim = new Simulator(5L);
        Set<Long> delays = new HashSet<>();
        for (int draw = 0; draw < 50; draw++) {
            delays.add(policy.decide(5, FailureKind.TIMED_OUT, sim).delayMicros());
        }
        assertTrue(delays.size() > 40, "jitter produced only " + delays.size() + " distinct delays");
    }

    @Test
    void theCapItselfIsDrawable() {
        ExponentialBackoffWithJitter policy = new ExponentialBackoffWithJitter(5, 1, 1);
        Simulator sim = new Simulator(5L);
        boolean sawCap = false;
        for (int draw = 0; draw < 50 && !sawCap; draw++) {
            sawCap = policy.decide(1, FailureKind.TIMED_OUT, sim).delayMicros() == 1L;
        }
        assertTrue(sawCap, "full jitter draws from [0, cap] inclusive");
    }

    @Test
    void widerCapAllowsLongerDelays() {
        ExponentialBackoffWithJitter policy = new ExponentialBackoffWithJitter(10, 1_000, 1_000_000);
        assertTrue(maxDelay(policy, 8, 300) > maxDelay(policy, 1, 300),
                "later attempts should be able to wait longer");
    }

    @Test
    void sameSeedYieldsIdenticalDelaysDifferentSeedDiffers() {
        assertEquals(delays(42L), delays(42L));
        assertNotEquals(delays(42L), delays(43L));
    }

    @Test
    void consumesExactlyOneDrawPerDecision() {
        Simulator sim = new Simulator(7L);
        Simulator reference = new Simulator(7L);
        new ExponentialBackoffWithJitter(5, 1_000, 100_000).decide(1, FailureKind.TIMED_OUT, sim);
        reference.random().nextLong(1_001);
        assertEquals(reference.random().nextLong(), sim.random().nextLong());
    }

    @Test
    void givesUpPastTheRetryLimit() {
        ExponentialBackoffWithJitter policy = new ExponentialBackoffWithJitter(2, 1_000, 100_000);
        Simulator sim = new Simulator(1L);
        assertTrue(policy.decide(2, FailureKind.TIMED_OUT, sim).retry());
        assertFalse(policy.decide(3, FailureKind.TIMED_OUT, sim).retry());
    }

    @Test
    void givingUpDrawsNoRandomness() {
        Simulator sim = new Simulator(11L);
        long expected = new Simulator(11L).random().nextLong();
        new ExponentialBackoffWithJitter(1, 1_000, 100_000).decide(2, FailureKind.TIMED_OUT, sim);
        assertEquals(expected, sim.random().nextLong());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoffWithJitter(-1, 1_000, 2_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoffWithJitter(1, 0, 2_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoffWithJitter(1, 2_000, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoffWithJitter(1, 1_000, Long.MAX_VALUE));
    }

    private static long maxDelay(ExponentialBackoffWithJitter policy, int attempt, int draws) {
        Simulator sim = new Simulator(3L);
        long max = 0;
        for (int draw = 0; draw < draws; draw++) {
            max = Math.max(max, policy.decide(attempt, FailureKind.TIMED_OUT, sim).delayMicros());
        }
        return max;
    }

    private static List<Long> delays(long seed) {
        ExponentialBackoffWithJitter policy = new ExponentialBackoffWithJitter(10, 1_000, 100_000);
        Simulator sim = new Simulator(seed);
        List<Long> delays = new ArrayList<>();
        for (int attempt = 1; attempt <= 10; attempt++) {
            delays.add(policy.decide(attempt, FailureKind.TIMED_OUT, sim).delayMicros());
        }
        return delays;
    }
}
