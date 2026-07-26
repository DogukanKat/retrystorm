package retrystorm.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhaseExperimentTest {

    private static final List<Double> UTILS = List.of(0.6, 0.9);
    private static final List<Long> SEEDS = List.of(42L, 43L);

    @Test
    void breakersHelpAtLowUtilisationButHarmTheBaselineAtHigh() {
        List<PhaseRow> rows = PhaseExperiment.run(CanonicalExperiment.scenario(), UTILS, SEEDS);
        assertEquals(3 * UTILS.size() * SEEDS.size(), rows.size());

        for (long seed : SEEDS) {
            double retryOnlyLow = instability(rows, "retry-only", 0.6, seed);
            double failFastLow = instability(rows, "fail-fast", 0.6, seed);
            assertTrue(retryOnlyLow < 60,
                    "retry-only is steady at low utilisation, seed " + seed + ": " + retryOnlyLow);
            assertTrue(failFastLow > 2 * retryOnlyLow,
                    "fail-fast already oscillates at low utilisation, seed " + seed + ": " + failFastLow);

            double noRetryBase = baseline(rows, "no-retry", 0.9, seed);
            assertTrue(noRetryBase > 1.2 * baseline(rows, "retry-only", 0.9, seed),
                    "at high utilisation the retry-only breaker collapses the baseline, seed " + seed);
            assertTrue(noRetryBase > 1.2 * baseline(rows, "fail-fast", 0.9, seed),
                    "at high utilisation the fail-fast breaker collapses the baseline, seed " + seed);
        }
    }

    @Test
    void writesHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("phase.csv");
        PhaseExperiment.writeCsv(file, List.of(
                new PhaseRow("no-retry", 0.9, 42, 868.3, 851.6, 115.3),
                new PhaseRow("fail-fast", 0.9, 42, 542.8, 550.1, 348.5)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertEquals("policy,utilisation,seed,baseline_goodput,recovery_goodput,recovery_instability",
                lines.get(0));
        assertEquals("no-retry,0.90,42,868.300,851.600,115.300", lines.get(1));
        assertEquals("fail-fast,0.90,42,542.800,550.100,348.500", lines.get(2));
    }

    private static double instability(List<PhaseRow> rows, String policy, double util, long seed) {
        return find(rows, policy, util, seed).recoveryInstability();
    }

    private static double baseline(List<PhaseRow> rows, String policy, double util, long seed) {
        return find(rows, policy, util, seed).baselineGoodputPerSecond();
    }

    private static PhaseRow find(List<PhaseRow> rows, String policy, double util, long seed) {
        return rows.stream()
                .filter(row -> row.policy().equals(policy)
                        && row.utilisation() == util && row.seed() == seed)
                .findFirst()
                .orElseThrow();
    }
}
