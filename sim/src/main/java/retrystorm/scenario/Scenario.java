package retrystorm.scenario;

import retrystorm.sim.RateSchedule;

/**
 * All the inputs to one run, in code rather than an external config format.
 * The same {@code seed} makes a run reproducible; every stochastic component
 * draws from the one engine random source it names.
 */
public record Scenario(
        long seed,
        long horizonMicros,
        long bucketMicros,
        int workers,
        long meanServiceMicros,
        int queueCapacity,
        double baseRatePerSecond,
        double spikeRatePerSecond,
        long spikeStartMicros,
        long spikeEndMicros,
        long attemptTimeoutMicros,
        int maxAttempts) {

    public Scenario {
        if (horizonMicros <= 0) {
            throw new IllegalArgumentException("horizonMicros must be positive: " + horizonMicros);
        }
        if (bucketMicros <= 0 || bucketMicros > horizonMicros) {
            throw new IllegalArgumentException("bucketMicros must be in (0, horizon]: " + bucketMicros);
        }
        if (spikeEndMicros > horizonMicros) {
            throw new IllegalArgumentException("spike window ends after the horizon");
        }
    }

    public RateSchedule rateSchedule() {
        return new RateSchedule(baseRatePerSecond, spikeRatePerSecond, spikeStartMicros, spikeEndMicros);
    }

    public Scenario withSeed(long newSeed) {
        return new Scenario(newSeed, horizonMicros, bucketMicros, workers, meanServiceMicros, queueCapacity,
                baseRatePerSecond, spikeRatePerSecond, spikeStartMicros, spikeEndMicros,
                attemptTimeoutMicros, maxAttempts);
    }

    public Scenario withArrivalRates(double newBaseRatePerSecond, double newSpikeRatePerSecond) {
        return new Scenario(seed, horizonMicros, bucketMicros, workers, meanServiceMicros, queueCapacity,
                newBaseRatePerSecond, newSpikeRatePerSecond, spikeStartMicros, spikeEndMicros,
                attemptTimeoutMicros, maxAttempts);
    }
}
