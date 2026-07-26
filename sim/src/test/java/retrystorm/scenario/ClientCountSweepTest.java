package retrystorm.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientCountSweepTest {

    @Test
    void backoffJitterCrossesFromRecoveryToCollapseWhileTokenBucketHolds() {
        List<SweepRow> rows = ClientCountSweep.run(
                CanonicalExperiment.scenario(), ClientCountSweep.DEFAULT_CLIENT_COUNTS);
        assertEquals(2 * ClientCountSweep.DEFAULT_CLIENT_COUNTS.size(), rows.size());

        int fewest = Collections.min(ClientCountSweep.DEFAULT_CLIENT_COUNTS);
        int most = Collections.max(ClientCountSweep.DEFAULT_CLIENT_COUNTS);

        SweepRow jitterFew = find(rows, fewest, "backoff-jitter");
        SweepRow jitterMany = find(rows, most, "backoff-jitter");
        assertTrue(recovered(jitterFew), "backoff-jitter should recover with few clients: " + jitterFew);
        assertTrue(collapsed(jitterMany), "backoff-jitter should collapse with many clients: " + jitterMany);

        for (SweepRow row : rows) {
            if (row.policy().equals("token-bucket")) {
                assertTrue(recovered(row), "token-bucket should recover at every count: " + row);
            }
        }
    }

    @Test
    void writesHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sweep.csv");
        ClientCountSweep.writeCsv(file, List.of(
                new SweepRow(25, "backoff-jitter", 151.8, 153.3),
                new SweepRow(100, "backoff-jitter", 602.1, 0.0)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertEquals("client_count,policy,baseline_goodput,recovery_goodput", lines.get(0));
        assertEquals("25,backoff-jitter,151.800,153.300", lines.get(1));
        assertEquals("100,backoff-jitter,602.100,0.000", lines.get(2));
    }

    private static boolean recovered(SweepRow row) {
        return row.recoveryGoodputPerSecond() > 0.80 * row.baselineGoodputPerSecond();
    }

    private static boolean collapsed(SweepRow row) {
        return row.recoveryGoodputPerSecond() < 0.05 * row.baselineGoodputPerSecond();
    }

    private static SweepRow find(List<SweepRow> rows, int clientCount, String policy) {
        return rows.stream()
                .filter(row -> row.clientCount() == clientCount && row.policy().equals(policy))
                .findFirst()
                .orElseThrow();
    }
}
