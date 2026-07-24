package retrystorm.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import retrystorm.engine.Simulator;

class ServerTest {

    private static final long MEAN_SERVICE_MICROS = 1_000;

    @Test
    void rejectsInvalidConfiguration() {
        Simulator sim = new Simulator(1L);
        ExponentialServiceTime service = new ExponentialServiceTime(MEAN_SERVICE_MICROS);
        assertThrows(IllegalArgumentException.class, () -> new Server(sim, 0, 1, service));
        assertThrows(IllegalArgumentException.class, () -> new Server(sim, 1, -1, service));
        assertThrows(NullPointerException.class, () -> new Server(null, 1, 1, service));
        assertThrows(NullPointerException.class, () -> new Server(sim, 1, 1, null));
    }

    @Test
    void rejectsNullCallback() {
        Server server = server(new Simulator(1L), 1, 1);
        assertThrows(NullPointerException.class, () -> server.submit(null));
    }

    @Test
    void servesImmediatelyWhenWorkerIsFree() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 0);
        List<Long> served = new ArrayList<>();
        assertTrue(server.submit(() -> served.add(sim.now())));
        assertEquals(1, server.busyWorkers());
        assertEquals(0, server.queueDepth());
        sim.runToCompletion();
        assertEquals(1, served.size());
        assertEquals(0, server.busyWorkers());
    }

    @Test
    void schedulesServiceTimeDrawnFromDistribution() {
        long seed = 99L;
        long expected = new ExponentialServiceTime(MEAN_SERVICE_MICROS).sampleMicros(new Random(seed));
        Simulator sim = new Simulator(seed);
        Server server = server(sim, 1, 0);
        List<Long> served = new ArrayList<>();
        server.submit(() -> served.add(sim.now()));
        sim.runToCompletion();
        assertEquals(List.of(expected), served);
    }

    @Test
    void queuesWorkWhenAllWorkersBusy() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 2);
        assertTrue(server.submit(() -> {
        }));
        assertTrue(server.submit(() -> {
        }));
        assertEquals(1, server.busyWorkers());
        assertEquals(1, server.queueDepth());
    }

    @Test
    void rejectsWhenWorkersBusyAndQueueFull() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 1);
        assertTrue(server.submit(() -> {
        }));
        assertTrue(server.submit(() -> {
        }));
        assertFalse(server.submit(() -> {
        }));
        assertEquals(1, server.queueDepth());
    }

    @Test
    void rejectedWorkNeverRunsItsCallback() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 0);
        List<String> served = new ArrayList<>();
        server.submit(() -> served.add("accepted"));
        assertFalse(server.submit(() -> served.add("rejected")));
        sim.runToCompletion();
        assertEquals(List.of("accepted"), served);
    }

    @Test
    void zeroCapacityQueueRejectsAsSoonAsWorkersAreBusy() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 2, 0);
        assertTrue(server.submit(() -> {
        }));
        assertTrue(server.submit(() -> {
        }));
        assertFalse(server.submit(() -> {
        }));
        assertEquals(0, server.queueDepth());
    }

    @Test
    void queuedWorkIsServedInFifoOrder() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 10);
        List<Integer> served = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int index = i;
            server.submit(() -> served.add(index));
        }
        sim.runToCompletion();
        assertEquals(List.of(0, 1, 2, 3, 4), served);
    }

    @Test
    void freedWorkerPicksUpNextQueuedItem() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 5);
        List<Long> served = new ArrayList<>();
        server.submit(() -> served.add(sim.now()));
        server.submit(() -> served.add(sim.now()));
        assertEquals(1, server.queueDepth());
        sim.runToCompletion();
        assertEquals(2, served.size());
        assertTrue(served.get(1) > served.get(0), "second item should finish later");
        assertEquals(0, server.queueDepth());
        assertEquals(0, server.busyWorkers());
    }

    @Test
    void runsWorkersConcurrentlyUpToTheirCount() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 3, 0);
        server.submit(() -> {
        });
        server.submit(() -> {
        });
        server.submit(() -> {
        });
        assertEquals(3, server.busyWorkers());
        assertFalse(server.submit(() -> {
        }));
    }

    @Test
    void neverExceedsWorkerCountWhenCallbackResubmits() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 1, 5);
        List<Integer> observed = new ArrayList<>();
        server.submit(() -> {
            server.submit(() -> {
            });
            sim.schedule(0, () -> observed.add(server.busyWorkers()));
        });
        server.submit(() -> {
        });
        sim.runToCompletion();
        assertTrue(observed.stream().allMatch(busy -> busy <= 1),
                "busyWorkers exceeded worker count: " + observed);
    }

    @Test
    void combinesMultipleWorkersWithABoundedQueue() {
        Simulator sim = new Simulator(1L);
        Server server = server(sim, 2, 2);
        List<Integer> served = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int index = i;
            assertTrue(server.submit(() -> served.add(index)), "item " + index + " should be accepted");
        }
        assertEquals(2, server.busyWorkers());
        assertEquals(2, server.queueDepth());
        assertFalse(server.submit(() -> served.add(4)));
        sim.runToCompletion();
        assertEquals(4, served.size());
        assertEquals(List.of(0, 1, 2, 3), served.stream().sorted().toList());
        assertEquals(0, server.busyWorkers());
        assertEquals(0, server.queueDepth());
    }

    @Test
    void rejectedWorkDrawsNoServiceTime() {
        long seed = 7L;
        Random reference = new Random(seed);
        ExponentialServiceTime distribution = new ExponentialServiceTime(MEAN_SERVICE_MICROS);
        long firstDraw = distribution.sampleMicros(reference);
        long secondDraw = distribution.sampleMicros(reference);

        Simulator sim = new Simulator(seed);
        Server server = server(sim, 1, 0);
        List<Long> served = new ArrayList<>();
        server.submit(() -> served.add(sim.now()));
        assertFalse(server.submit(() -> served.add(-1L)));
        sim.runToCompletion();
        server.submit(() -> served.add(sim.now()));
        sim.runToCompletion();

        assertEquals(List.of(firstDraw, firstDraw + secondDraw), served);
    }

    private static Server server(Simulator sim, int workers, int queueCapacity) {
        return new Server(sim, workers, queueCapacity, new ExponentialServiceTime(MEAN_SERVICE_MICROS));
    }
}
