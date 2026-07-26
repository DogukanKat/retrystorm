package retrystorm.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiSeedValidationTest {

    private static final Set<String> COLLAPSING = Set.of("fixed-retry", "exponential-backoff", "backoff-jitter");
    private static final Set<String> RECOVERING = Set.of("no-retry", "token-bucket", "circuit-breaker");

    private static final long SECOND = 1_000_000;

    @Test
    void collapseVersusRecoveryHoldsAcrossEveryDefaultSeed() {
        List<ValidationRow> rows = MultiSeedValidation.run(
                CanonicalExperiment.scenario(), MultiSeedValidation.DEFAULT_SEEDS);
        assertEquals(6 * MultiSeedValidation.DEFAULT_SEEDS.size(), rows.size());

        for (ValidationRow row : rows) {
            String where = row.policy() + " (seed " + row.seed() + ")";
            if (COLLAPSING.contains(row.policy())) {
                assertTrue(row.recoveryGoodputPerSecond() < 0.05 * row.baselineGoodputPerSecond(),
                        where + " should stay collapsed but recovered to " + row.recoveryGoodputPerSecond());
                assertTrue(row.overloadRetries() > 10_000,
                        where + " should have amplified load with retries, had " + row.overloadRetries());
            } else if (RECOVERING.contains(row.policy())) {
                assertTrue(row.recoveryGoodputPerSecond() > 0.80 * row.baselineGoodputPerSecond(),
                        where + " should recover but only reached " + row.recoveryGoodputPerSecond()
                                + " of baseline " + row.baselineGoodputPerSecond());
                assertTrue(row.overloadRetries() < 1_000,
                        where + " should not have flooded retries, had " + row.overloadRetries());
            } else {
                fail("unclassified policy: " + row.policy());
            }
        }
    }

    @Test
    void summaryIsDeterministicForTheSameSeeds() {
        Scenario small = new Scenario(1L, 5 * SECOND, SECOND, 2, 5_000, 20,
                300.0, 1_500.0, 2 * SECOND, 3 * SECOND, 20_000, 4);
        List<Long> seeds = List.of(1L, 2L);
        assertEquals(MultiSeedValidation.run(small, seeds), MultiSeedValidation.run(small, seeds));
    }

    @Test
    void writesHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("validation.csv");
        MultiSeedValidation.writeCsv(file, List.of(
                new ValidationRow("no-retry", 42, 596.9, 9.5, 593.9, 0),
                new ValidationRow("fixed-retry", 42, 592.2, 6.9, 0.0, 123966)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertEquals("policy,seed,baseline_goodput,overload_goodput,recovery_goodput,overload_retries",
                lines.get(0));
        assertEquals("no-retry,42,596.900,9.500,593.900,0", lines.get(1));
        assertEquals("fixed-retry,42,592.200,6.900,0.000,123966", lines.get(2));
    }
}
