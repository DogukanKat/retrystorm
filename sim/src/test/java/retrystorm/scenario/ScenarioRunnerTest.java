package retrystorm.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import retrystorm.metrics.BucketRow;
import retrystorm.policy.FixedRetry;
import retrystorm.policy.NoRetry;

class ScenarioRunnerTest {

    private static final long SECOND = 1_000_000;

    private static Scenario smallScenario(long seed) {
        return new Scenario(seed, 5 * SECOND, SECOND, 2, 5_000, 20,
                300.0, 300.0, 0, 0, 20_000, 3);
    }

    @Test
    void producesOneRowPerBucket() {
        List<BucketRow> rows = ScenarioRunner.run(smallScenario(1L), new NoRetry());
        assertEquals(5, rows.size());
        for (int b = 0; b < rows.size(); b++) {
            assertEquals(b, rows.get(b).bucketIndex());
            assertEquals((long) b * SECOND, rows.get(b).bucketStartMicros());
        }
    }

    @Test
    void sameSeedProducesIdenticalRowsDifferentSeedDiffers() {
        List<BucketRow> a = ScenarioRunner.run(smallScenario(7L), new NoRetry());
        List<BucketRow> b = ScenarioRunner.run(smallScenario(7L), new NoRetry());
        List<BucketRow> c = ScenarioRunner.run(smallScenario(8L), new NoRetry());
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void samplesQueueDepthForEveryBucket() {
        Scenario overloaded = new Scenario(3L, 5 * SECOND, SECOND, 1, 20_000, 50,
                2_000.0, 2_000.0, 0, 0, 100_000, 3);
        List<BucketRow> rows = ScenarioRunner.run(overloaded, new FixedRetry(2, 10_000));
        long busyBuckets = rows.stream().filter(row -> row.queueDepth() > 0).count();
        assertTrue(busyBuckets > 0, "an overloaded server should show a non-empty queue at some edge");
    }

    @Test
    void offeredLoadTracksTheArrivalRate() {
        List<BucketRow> rows = ScenarioRunner.run(smallScenario(5L), new NoRetry());
        long offered = rows.stream().mapToLong(BucketRow::offered).sum();
        assertTrue(offered > 5 * 300 * 0.7 && offered < 5 * 300 * 1.3,
                "roughly 300/s over 5s, was " + offered);
    }

    @Test
    void goodputCannotExceedOfferedLoad() {
        List<BucketRow> rows = ScenarioRunner.run(smallScenario(9L), new NoRetry());
        long offered = rows.stream().mapToLong(BucketRow::offered).sum();
        long goodput = rows.stream().mapToLong(BucketRow::goodput).sum();
        assertTrue(goodput <= offered, "goodput " + goodput + " exceeded offered " + offered);
    }
}
