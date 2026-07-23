package retrystorm.engine;

record Event(long timeMicros, long seq, Runnable action) implements Comparable<Event> {

    Event {
        if (timeMicros < 0) {
            throw new IllegalArgumentException("event time must be non-negative: " + timeMicros);
        }
        if (action == null) {
            throw new NullPointerException("action");
        }
    }

    @Override
    public int compareTo(Event other) {
        int byTime = Long.compare(timeMicros, other.timeMicros);
        return byTime != 0 ? byTime : Long.compare(seq, other.seq);
    }
}
