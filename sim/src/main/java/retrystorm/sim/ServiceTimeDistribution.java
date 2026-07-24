package retrystorm.sim;

import java.util.Random;

/** Draws how long one unit of work occupies a worker, in microseconds. */
public sealed interface ServiceTimeDistribution permits ExponentialServiceTime {

    long sampleMicros(Random random);
}
