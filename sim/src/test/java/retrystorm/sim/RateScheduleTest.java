package retrystorm.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RateScheduleTest {

    @Test
    void usesBaseRateOutsideTheSpikeWindow() {
        RateSchedule schedule = new RateSchedule(100, 500, 1_000, 2_000);
        assertEquals(100, schedule.ratePerSecondAt(0));
        assertEquals(100, schedule.ratePerSecondAt(999));
        assertEquals(100, schedule.ratePerSecondAt(5_000));
    }

    @Test
    void usesSpikeRateInsideTheWindow() {
        RateSchedule schedule = new RateSchedule(100, 500, 1_000, 2_000);
        assertEquals(500, schedule.ratePerSecondAt(1_000));
        assertEquals(500, schedule.ratePerSecondAt(1_999));
    }

    @Test
    void spikeWindowExcludesItsEnd() {
        RateSchedule schedule = new RateSchedule(100, 500, 1_000, 2_000);
        assertEquals(100, schedule.ratePerSecondAt(2_000));
    }

    @Test
    void emptyWindowNeverSpikes() {
        RateSchedule schedule = new RateSchedule(100, 500, 1_000, 1_000);
        assertEquals(100, schedule.ratePerSecondAt(1_000));
    }

    @Test
    void constantScheduleNeverChanges() {
        RateSchedule schedule = RateSchedule.constant(250);
        assertEquals(250, schedule.ratePerSecondAt(0));
        assertEquals(250, schedule.ratePerSecondAt(10_000_000));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RateSchedule(0, 100, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RateSchedule(100, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RateSchedule(100, 500, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RateSchedule(100, 500, 2_000, 1_000));
    }
}
