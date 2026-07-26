package retrystorm.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import retrystorm.scenario.PhaseExperiment;
import retrystorm.scenario.Scenario;

class OpenTraceAnalysisTest {

    private static final long SECOND = 1_000_000;
    private static final List<Long> SEEDS = List.of(42L, 43L);

    private static Scenario smallScenario() {
        return new Scenario(1L, 3 * SECOND, SECOND, 2, 5_000, 20,
                800.0, 2_500.0, 1 * SECOND, 2 * SECOND, 20_000, 4);
    }

    @Test
    void producesSamplesWithOpenCountsWithinTheClientCount() {
        List<OpenTraceRow> rows = OpenTraceAnalysis.run(smallScenario(), SEEDS);
        assertFalse(rows.isEmpty());
        for (OpenTraceRow row : rows) {
            assertTrue(row.openCount() >= 0 && row.openCount() <= PhaseExperiment.HERD_CLIENTS,
                    "open count must be within [0, clients]: " + row);
            assertTrue(SEEDS.contains(row.seed()), "seed: " + row.seed());
        }
    }

    @Test
    void writesTheExpectedHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        List<OpenTraceRow> rows = OpenTraceAnalysis.run(smallScenario(), SEEDS);
        Path file = dir.resolve("open_trace.csv");
        OpenTraceAnalysis.writeCsv(file, rows);
        List<String> lines = Files.readAllLines(file);
        assertEquals("seed,time_s,open_count", lines.get(0));
        assertEquals(rows.size() + 1, lines.size());
    }
}
