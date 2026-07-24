package retrystorm.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RetryDecisionTest {

    @Test
    void giveUpCarriesNoDelay() {
        RetryDecision decision = RetryDecision.giveUp();
        assertFalse(decision.retry());
        assertEquals(0L, decision.delayMicros());
    }

    @Test
    void retryAfterKeepsDelay() {
        RetryDecision decision = RetryDecision.retryAfter(2_500);
        assertTrue(decision.retry());
        assertEquals(2_500L, decision.delayMicros());
    }

    @Test
    void allowsZeroDelayRetry() {
        assertTrue(RetryDecision.retryAfter(0).retry());
    }

    @Test
    void rejectsNegativeRetryDelay() {
        assertThrows(IllegalArgumentException.class, () -> RetryDecision.retryAfter(-1));
    }

    @Test
    void rejectsGiveUpCarryingDelay() {
        assertThrows(IllegalArgumentException.class, () -> new RetryDecision(false, 10));
    }
}
