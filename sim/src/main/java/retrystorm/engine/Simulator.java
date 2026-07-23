package retrystorm.engine;

import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;

public final class Simulator {

    private final PriorityQueue<Event> queue = new PriorityQueue<>();
    private final Random random;
    private long nowMicros;
    private long nextSeq;

    public Simulator(long seed) {
        this.random = new Random(seed);
    }

    public long now() {
        return nowMicros;
    }

    public Random random() {
        return random;
    }

    /** Schedules {@code action} to run {@code delayMicros} from now; negative delays are rejected. */
    public void schedule(long delayMicros, Runnable action) {
        Objects.requireNonNull(action, "action");
        if (delayMicros < 0) {
            throw new IllegalArgumentException("delay must be non-negative: " + delayMicros);
        }
        queue.add(new Event(nowMicros + delayMicros, nextSeq++, action));
    }

    /**
     * Runs every event with time up to and including {@code untilMicros} and
     * leaves the clock exactly at the horizon, even if the queue drains early;
     * events past the horizon stay queued.
     */
    public void run(long untilMicros) {
        if (untilMicros < nowMicros) {
            throw new IllegalArgumentException("horizon " + untilMicros + " precedes current time " + nowMicros);
        }
        while (!queue.isEmpty() && queue.peek().timeMicros() <= untilMicros) {
            Event event = queue.poll();
            nowMicros = event.timeMicros();
            event.action().run();
        }
        nowMicros = untilMicros;
    }

    /** Runs until the queue is empty, for self-terminating scenarios. */
    public void runToCompletion() {
        while (!queue.isEmpty()) {
            Event event = queue.poll();
            nowMicros = event.timeMicros();
            event.action().run();
        }
    }

    public int pendingEvents() {
        return queue.size();
    }
}
