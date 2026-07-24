package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class NoRetryTest {

    @Test
    void neverRetriesWhateverTheAttemptOrFailure() {
        NoRetry policy = new NoRetry();
        Simulator sim = new Simulator(1L);
        for (FailureKind kind : FailureKind.values()) {
            for (int attempt = 1; attempt <= 5; attempt++) {
                assertFalse(policy.decide(attempt, kind, sim).retry(),
                        "attempt " + attempt + " after " + kind);
            }
        }
    }

    @Test
    void drawsNoRandomness() {
        Simulator sim = new Simulator(1L);
        long before = sim.random().nextLong();
        Simulator other = new Simulator(1L);
        new NoRetry().decide(1, FailureKind.TIMED_OUT, other);
        org.junit.jupiter.api.Assertions.assertEquals(before, other.random().nextLong());
    }
}
