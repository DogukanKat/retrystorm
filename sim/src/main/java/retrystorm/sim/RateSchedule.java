package retrystorm.sim;

/**
 * Arrival rate over time: a baseline with one elevated window, which is the
 * transient overload the experiments are built around.
 */
public record RateSchedule(
        double baseRatePerSecond,
        double spikeRatePerSecond,
        long spikeStartMicros,
        long spikeEndMicros) {

    public RateSchedule {
        if (baseRatePerSecond <= 0) {
            throw new IllegalArgumentException("baseRatePerSecond must be positive: " + baseRatePerSecond);
        }
        if (spikeRatePerSecond <= 0) {
            throw new IllegalArgumentException("spikeRatePerSecond must be positive: " + spikeRatePerSecond);
        }
        if (spikeStartMicros < 0) {
            throw new IllegalArgumentException("spikeStartMicros must be non-negative: " + spikeStartMicros);
        }
        if (spikeEndMicros < spikeStartMicros) {
            throw new IllegalArgumentException("spike window ends before it starts");
        }
    }

    /** Constant rate with no overload window. */
    public static RateSchedule constant(double ratePerSecond) {
        return new RateSchedule(ratePerSecond, ratePerSecond, 0, 0);
    }

    public double ratePerSecondAt(long nowMicros) {
        boolean inSpike = nowMicros >= spikeStartMicros && nowMicros < spikeEndMicros;
        return inSpike ? spikeRatePerSecond : baseRatePerSecond;
    }
}
