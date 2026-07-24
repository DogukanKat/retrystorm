package retrystorm.sim;

import java.util.Objects;
import java.util.Random;

/** Exponentially distributed service time with the given mean. */
public record ExponentialServiceTime(long meanServiceMicros) implements ServiceTimeDistribution {

    public ExponentialServiceTime {
        if (meanServiceMicros <= 0) {
            throw new IllegalArgumentException("meanServiceMicros must be positive: " + meanServiceMicros);
        }
    }

    @Override
    public long sampleMicros(Random random) {
        Objects.requireNonNull(random, "random");
        double sample = -meanServiceMicros * Math.log(1.0 - random.nextDouble());
        // Floored at one microsecond so a worker is never occupied for zero time.
        return Math.max(1L, Math.round(sample));
    }
}
