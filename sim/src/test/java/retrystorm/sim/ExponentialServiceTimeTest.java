package retrystorm.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ExponentialServiceTimeTest {

    @Test
    void rejectsNonPositiveMean() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialServiceTime(0));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialServiceTime(-1));
    }

    @Test
    void rejectsNullRandom() {
        assertThrows(NullPointerException.class, () -> new ExponentialServiceTime(100).sampleMicros(null));
    }

    @Test
    void sameSeedYieldsIdenticalSamplesDifferentSeedDiffers() {
        List<Long> first = samples(1_000, 42L, 50);
        List<Long> second = samples(1_000, 42L, 50);
        List<Long> other = samples(1_000, 43L, 50);
        assertEquals(first, second);
        assertNotEquals(first, other);
    }

    @Test
    void samplesAreAtLeastOneMicrosecond() {
        for (long sample : samples(1, 7L, 500)) {
            assertTrue(sample >= 1, "sample was " + sample);
        }
    }

    @Test
    void sampleMeanIsCloseToConfiguredMean() {
        long mean = 10_000;
        List<Long> samples = samples(mean, 12345L, 20_000);
        double average = samples.stream().mapToLong(Long::longValue).average().orElseThrow();
        assertTrue(Math.abs(average - mean) < mean * 0.05,
                "average " + average + " too far from mean " + mean);
    }

    private static List<Long> samples(long meanMicros, long seed, int count) {
        ExponentialServiceTime distribution = new ExponentialServiceTime(meanMicros);
        Random random = new Random(seed);
        List<Long> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            samples.add(distribution.sampleMicros(random));
        }
        return samples;
    }
}
