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

import retrystorm.scenario.Scenario;

class SpikeAnalysisTest {

    private static final long SECOND = 1_000_000;

    private static Scenario smallScenario() {
        return new Scenario(1L, 5 * SECOND, SECOND, 2, 5_000, 20,
                300.0, 1_200.0, 2 * SECOND, 3 * SECOND, 20_000, 4);
    }

    @Test
    void producesRecoveryRowsWithFirstAttemptP99NotAboveOverall() {
        List<SpikeRow> rows = SpikeAnalysis.run(smallScenario());
        assertFalse(rows.isEmpty(), "recovery buckets should yield rows");
        for (SpikeRow row : rows) {
            assertTrue(row.successes() > 0, "a recorded bucket has successes: " + row);
            assertEquals(row.successes() >= row.multiAttempt(), true);
            if (row.p99FirstMicros() >= 0) {
                assertTrue(row.p99FirstMicros() <= row.p99AllMicros(),
                        "first-attempt p99 must not exceed all-successes p99: " + row);
            }
        }
    }

    @Test
    void writesTheExpectedHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        List<SpikeRow> rows = SpikeAnalysis.run(smallScenario());
        Path file = dir.resolve("spike_analysis.csv");
        SpikeAnalysis.writeCsv(file, rows);
        List<String> lines = Files.readAllLines(file);
        assertEquals("policy,time_s,successes,multi_attempt,p99_all_ms,p99_first_ms,p99_multi_ms",
                lines.get(0));
        assertEquals(rows.size() + 1, lines.size());
    }
}
