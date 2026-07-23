package retrystorm.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventTest {

    private static final Runnable NOOP = () -> {
    };

    @Test
    void ordersByTimeFirst() {
        Event earlier = new Event(10, 9, NOOP);
        Event later = new Event(20, 1, NOOP);
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
    }

    @Test
    void breaksTiesBySeq() {
        Event first = new Event(10, 1, NOOP);
        Event second = new Event(10, 2, NOOP);
        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
    }

    @Test
    void comparesEqualForSameTimeAndSeq() {
        assertEquals(0, new Event(10, 1, NOOP).compareTo(new Event(10, 1, NOOP)));
    }

    @Test
    void ordersExtremeTimesWithoutOverflow() {
        Event zero = new Event(0, 0, NOOP);
        Event max = new Event(Long.MAX_VALUE, 0, NOOP);
        assertTrue(zero.compareTo(max) < 0);
        assertTrue(max.compareTo(zero) > 0);
    }

    @Test
    void ordersExtremeSeqWithoutOverflow() {
        Event low = new Event(10, 0, NOOP);
        Event high = new Event(10, Long.MAX_VALUE, NOOP);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
    }

    @Test
    void allowsZeroTime() {
        assertEquals(0L, new Event(0, 0, NOOP).timeMicros());
    }

    @Test
    void rejectsNegativeTime() {
        assertThrows(IllegalArgumentException.class, () -> new Event(-1, 0, NOOP));
    }

    @Test
    void rejectsNullAction() {
        assertThrows(NullPointerException.class, () -> new Event(0, 0, null));
    }
}
