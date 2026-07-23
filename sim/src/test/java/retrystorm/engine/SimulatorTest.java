package retrystorm.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SimulatorTest {

    @Test
    void startsAtTimeZeroWithEmptyQueue() {
        Simulator sim = new Simulator(1L);
        assertEquals(0L, sim.now());
        assertEquals(0, sim.pendingEvents());
    }

    @Test
    void pendingEventsCountsScheduledEvents() {
        Simulator sim = new Simulator(1L);
        sim.schedule(10, () -> {
        });
        sim.schedule(20, () -> {
        });
        assertEquals(2, sim.pendingEvents());
    }

    @Test
    void processesEventsInTimeOrder() {
        Simulator sim = new Simulator(1L);
        List<Integer> order = new ArrayList<>();
        sim.schedule(30, () -> order.add(3));
        sim.schedule(10, () -> order.add(1));
        sim.schedule(20, () -> order.add(2));
        sim.run(100);
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void runsSameInstantInScheduleOrder() {
        Simulator sim = new Simulator(1L);
        List<Integer> order = new ArrayList<>();
        sim.schedule(10, () -> order.add(1));
        sim.schedule(10, () -> order.add(2));
        sim.schedule(10, () -> order.add(3));
        sim.run(100);
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void interleavesCollidingInstantsInScheduleOrder() {
        Simulator sim = new Simulator(1L);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int index = i;
            sim.schedule(i % 3, () -> order.add(index));
        }
        sim.run(100);
        assertEquals(List.of(0, 3, 6, 9, 1, 4, 7, 2, 5, 8), order);
    }

    @Test
    void clockEqualsEventTimeInsideAction() {
        Simulator sim = new Simulator(1L);
        List<Long> observed = new ArrayList<>();
        sim.schedule(15, () -> observed.add(sim.now()));
        sim.schedule(25, () -> observed.add(sim.now()));
        sim.run(100);
        assertEquals(List.of(15L, 25L), observed);
    }

    @Test
    void actionsCanScheduleFurtherEvents() {
        Simulator sim = new Simulator(1L);
        List<Long> times = new ArrayList<>();
        sim.schedule(10, () -> {
            times.add(sim.now());
            sim.schedule(5, () -> times.add(sim.now()));
        });
        sim.run(100);
        assertEquals(List.of(10L, 15L), times);
    }

    @Test
    void scheduleDelayIsRelativeToNow() {
        Simulator sim = new Simulator(1L);
        List<Long> times = new ArrayList<>();
        sim.schedule(10, () -> sim.schedule(10, () -> times.add(sim.now())));
        sim.run(100);
        assertEquals(List.of(20L), times);
    }

    @Test
    void runsEventScheduledExactlyAtHorizon() {
        Simulator sim = new Simulator(1L);
        List<Long> ran = new ArrayList<>();
        sim.schedule(100, () -> ran.add(sim.now()));
        sim.run(100);
        assertEquals(List.of(100L), ran);
        assertEquals(0, sim.pendingEvents());
        assertEquals(100L, sim.now());
    }

    @Test
    void horizonLeavesLateEventsQueuedAndClockOnHorizon() {
        Simulator sim = new Simulator(1L);
        List<Long> ran = new ArrayList<>();
        sim.schedule(50, () -> ran.add(sim.now()));
        sim.schedule(150, () -> ran.add(sim.now()));
        sim.run(100);
        assertEquals(List.of(50L), ran);
        assertEquals(100L, sim.now());
        assertEquals(1, sim.pendingEvents());
    }

    @Test
    void lateEventRunsInSubsequentRun() {
        Simulator sim = new Simulator(1L);
        List<Long> ran = new ArrayList<>();
        sim.schedule(150, () -> ran.add(sim.now()));
        sim.run(100);
        assertEquals(List.of(), ran);
        sim.run(200);
        assertEquals(List.of(150L), ran);
        assertEquals(0, sim.pendingEvents());
        assertEquals(200L, sim.now());
    }

    @Test
    void successiveRunsAdvanceClockAndProcessInOrder() {
        Simulator sim = new Simulator(1L);
        List<Long> ran = new ArrayList<>();
        sim.schedule(30, () -> ran.add(sim.now()));
        sim.schedule(80, () -> ran.add(sim.now()));
        sim.run(50);
        assertEquals(List.of(30L), ran);
        assertEquals(50L, sim.now());
        sim.run(100);
        assertEquals(List.of(30L, 80L), ran);
        assertEquals(100L, sim.now());
    }

    @Test
    void clockLandsOnHorizonWhenQueueDrainsEarly() {
        Simulator sim = new Simulator(1L);
        sim.schedule(10, () -> {
        });
        sim.run(100);
        assertEquals(100L, sim.now());
        assertEquals(0, sim.pendingEvents());
    }

    @Test
    void runOnEmptyQueueAdvancesClockToHorizon() {
        Simulator sim = new Simulator(1L);
        sim.run(250);
        assertEquals(250L, sim.now());
    }

    @Test
    void runWithHorizonEqualToNowIsAllowed() {
        Simulator sim = new Simulator(1L);
        sim.run(100);
        sim.run(100);
        assertEquals(100L, sim.now());
    }

    @Test
    void zeroDelayRunsAfterAlreadyQueuedSameInstant() {
        Simulator sim = new Simulator(1L);
        List<String> order = new ArrayList<>();
        sim.schedule(10, () -> {
            order.add("a");
            sim.schedule(0, () -> order.add("a-zero"));
        });
        sim.schedule(10, () -> order.add("b"));
        sim.run(100);
        assertEquals(List.of("a", "b", "a-zero"), order);
    }

    @Test
    void runToCompletionDrainsQueue() {
        Simulator sim = new Simulator(1L);
        List<Long> ran = new ArrayList<>();
        sim.schedule(10, () -> ran.add(sim.now()));
        sim.schedule(500, () -> ran.add(sim.now()));
        sim.runToCompletion();
        assertEquals(List.of(10L, 500L), ran);
        assertEquals(0, sim.pendingEvents());
        assertEquals(500L, sim.now());
    }

    @Test
    void runToCompletionRunsCascadingEvents() {
        Simulator sim = new Simulator(1L);
        List<Long> times = new ArrayList<>();
        sim.schedule(10, () -> {
            times.add(sim.now());
            sim.schedule(10, () -> {
                times.add(sim.now());
                sim.schedule(10, () -> times.add(sim.now()));
            });
        });
        sim.runToCompletion();
        assertEquals(List.of(10L, 20L, 30L), times);
        assertEquals(30L, sim.now());
    }

    @Test
    void runToCompletionOnEmptyQueueLeavesClockUnchanged() {
        Simulator sim = new Simulator(1L);
        sim.run(40);
        sim.runToCompletion();
        assertEquals(40L, sim.now());
    }

    @Test
    void rejectsNegativeDelay() {
        Simulator sim = new Simulator(1L);
        assertThrows(IllegalArgumentException.class, () -> sim.schedule(-1, () -> {
        }));
    }

    @Test
    void rejectsNegativeDelayEvenWhenResultingTimeIsValid() {
        Simulator sim = new Simulator(1L);
        sim.run(100);
        assertThrows(IllegalArgumentException.class, () -> sim.schedule(-50, () -> {
        }));
    }

    @Test
    void rejectsNullAction() {
        Simulator sim = new Simulator(1L);
        assertThrows(NullPointerException.class, () -> sim.schedule(0, null));
    }

    @Test
    void rejectsHorizonBeforeNow() {
        Simulator sim = new Simulator(1L);
        sim.run(100);
        assertThrows(IllegalArgumentException.class, () -> sim.run(50));
    }

    @Test
    void exposesSingleSharedRandom() {
        Simulator sim = new Simulator(1L);
        assertSame(sim.random(), sim.random());
    }

    @Test
    void sameSeedYieldsIdenticalTraceDifferentSeedDiffers() {
        List<Long> traceA = randomTrace(42L);
        List<Long> traceB = randomTrace(42L);
        List<Long> traceC = randomTrace(43L);
        assertEquals(traceA, traceB);
        assertNotEquals(traceA, traceC);
    }

    private static List<Long> randomTrace(long seed) {
        Simulator sim = new Simulator(seed);
        List<Long> draws = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            sim.schedule(i % 5, () -> draws.add(sim.random().nextLong()));
        }
        sim.run(100);
        return draws;
    }
}
