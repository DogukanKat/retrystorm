package retrystorm.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import retrystorm.scenario.Scenario;

class BreakerStateAnalysisTest {

    private static final long SECOND = 1_000_000;
    private static final List<Double> UTILISATIONS = List.of(0.5, 0.9);
    private static final List<Long> SEEDS = List.of(1L, 2L);

    private static Scenario smallScenario() {
        return new Scenario(1L, 5 * SECOND, SECOND, 2, 5_000, 20,
                300.0, 1_200.0, 2 * SECOND, 3 * SECOND, 20_000, 4);
    }

    @Test
    void producesOneRowPerBreakerUtilisationSeedWithFractionsInRange() {
        List<BreakerStateRow> rows = BreakerStateAnalysis.run(smallScenario(), UTILISATIONS, SEEDS);
        assertEquals(2 * UTILISATIONS.size() * SEEDS.size(), rows.size());
        for (BreakerStateRow row : rows) {
            assertTrue(Set.of("retry-only", "fail-fast").contains(row.breaker()), "breaker: " + row.breaker());
            assertTrue(row.baselineTrippedFraction() >= 0.0 && row.baselineTrippedFraction() <= 1.0,
                    "fraction must be in [0, 1]: " + row);
        }
    }

    @Test
    void writesTheExpectedHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        List<BreakerStateRow> rows = BreakerStateAnalysis.run(smallScenario(), UTILISATIONS, SEEDS);
        Path file = dir.resolve("breaker_state.csv");
        BreakerStateAnalysis.writeCsv(file, rows);
        List<String> lines = Files.readAllLines(file);
        assertEquals("breaker,utilisation,seed,baseline_tripped_fraction", lines.get(0));
        assertEquals(rows.size() + 1, lines.size());
    }
}
