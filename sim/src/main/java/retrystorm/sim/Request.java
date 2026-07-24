package retrystorm.sim;

import java.util.Objects;

/**
 * One logical request, tracked across every attempt made on its behalf until
 * it reaches a terminal {@link Outcome}. Latency is measured from
 * {@code createdAtMicros}, so retries are charged to the original request.
 */
public final class Request {

    private final long id;
    private final long createdAtMicros;
    private int attempts;
    private Outcome outcome;

    public Request(long id, long createdAtMicros) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative: " + id);
        }
        if (createdAtMicros < 0) {
            throw new IllegalArgumentException("createdAtMicros must be non-negative: " + createdAtMicros);
        }
        this.id = id;
        this.createdAtMicros = createdAtMicros;
    }

    public long id() {
        return id;
    }

    public long createdAtMicros() {
        return createdAtMicros;
    }

    public int attempts() {
        return attempts;
    }

    /** Terminal outcome, or {@code null} while the request is still in flight. */
    public Outcome outcome() {
        return outcome;
    }

    public boolean isSettled() {
        return outcome != null;
    }

    void recordAttempt() {
        attempts++;
    }

    void settle(Outcome finalOutcome) {
        Objects.requireNonNull(finalOutcome, "finalOutcome");
        if (outcome != null) {
            throw new IllegalStateException("request " + id + " already settled as " + outcome);
        }
        outcome = finalOutcome;
    }
}
