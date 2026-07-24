package retrystorm.sim;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import retrystorm.engine.Simulator;

/**
 * A service with a fixed number of concurrent workers and a bounded FIFO
 * queue. Work submitted while every worker is busy waits in the queue, and is
 * rejected outright once the queue is full.
 *
 * <p>The server knows nothing about timeouts or retries. It serves everything
 * it accepts, so work whose caller has already given up still occupies a
 * worker. That wasted capacity is what sustains a retry storm.
 */
public final class Server {

    private final Simulator sim;
    private final int workers;
    private final int queueCapacity;
    private final ServiceTimeDistribution serviceTime;
    private final Deque<Runnable> waiting = new ArrayDeque<>();
    private int busyWorkers;

    public Server(Simulator sim, int workers, int queueCapacity, ServiceTimeDistribution serviceTime) {
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive: " + workers);
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity must be non-negative: " + queueCapacity);
        }
        this.sim = Objects.requireNonNull(sim, "sim");
        this.serviceTime = Objects.requireNonNull(serviceTime, "serviceTime");
        this.workers = workers;
        this.queueCapacity = queueCapacity;
    }

    /**
     * Offers work to the server. {@code onServed} runs once the work has been
     * processed; it is never run for rejected work.
     *
     * @return {@code true} if accepted, {@code false} if rejected because both
     *         the workers and the queue are full
     */
    public boolean submit(Runnable onServed) {
        Objects.requireNonNull(onServed, "onServed");
        if (busyWorkers < workers) {
            startService(onServed);
            return true;
        }
        if (waiting.size() < queueCapacity) {
            waiting.addLast(onServed);
            return true;
        }
        return false;
    }

    public int queueDepth() {
        return waiting.size();
    }

    public int busyWorkers() {
        return busyWorkers;
    }

    private void startService(Runnable onServed) {
        busyWorkers++;
        sim.schedule(serviceTime.sampleMicros(sim.random()), () -> finish(onServed));
    }

    private void finish(Runnable onServed) {
        busyWorkers--;
        // The freed worker takes the next queued item before the caller is
        // notified, so work submitted from within onServed cannot jump the queue.
        if (!waiting.isEmpty()) {
            startService(waiting.pollFirst());
        }
        onServed.run();
    }
}
