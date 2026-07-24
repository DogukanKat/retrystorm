package retrystorm.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequestTest {

    @Test
    void startsUnsettledWithNoAttempts() {
        Request request = new Request(7, 1_000);
        assertEquals(7, request.id());
        assertEquals(1_000, request.createdAtMicros());
        assertEquals(0, request.attempts());
        assertNull(request.outcome());
        assertFalse(request.isSettled());
    }

    @Test
    void recordAttemptCountsEveryAttempt() {
        Request request = new Request(1, 0);
        request.recordAttempt();
        request.recordAttempt();
        request.recordAttempt();
        assertEquals(3, request.attempts());
    }

    @Test
    void settleStoresTerminalOutcome() {
        Request request = new Request(1, 0);
        request.settle(Outcome.SUCCESS);
        assertEquals(Outcome.SUCCESS, request.outcome());
        assertTrue(request.isSettled());
    }

    @Test
    void settleRejectsSecondOutcome() {
        Request request = new Request(1, 0);
        request.settle(Outcome.TIMED_OUT);
        assertThrows(IllegalStateException.class, () -> request.settle(Outcome.SUCCESS));
        assertEquals(Outcome.TIMED_OUT, request.outcome());
    }

    @Test
    void settleRejectsNull() {
        Request request = new Request(1, 0);
        assertThrows(NullPointerException.class, () -> request.settle(null));
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new Request(-1, 0));
    }

    @Test
    void rejectsNegativeCreatedAt() {
        assertThrows(IllegalArgumentException.class, () -> new Request(1, -1));
    }
}
