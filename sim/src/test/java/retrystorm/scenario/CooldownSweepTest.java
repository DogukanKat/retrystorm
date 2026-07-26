package retrystorm.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CooldownSweepTest {

    private static final long SECOND = 1_000_000;
    private static final List<Long> COOLDOWNS = List.of(2 * SECOND, 60 * SECOND);
    private static final List<Integer> COUNTS = List.of(100);
    private static final List<Long> SEEDS = List.of(42L, 43L);

    @Test
    void longCooldownCollapsesFailFastRecoveryButNotRetryOnly() {
        List<CooldownRow> rows = CooldownSweep.run(
                CanonicalExperiment.scenario(), COOLDOWNS, COUNTS, SEEDS);
        assertEquals(2 * COOLDOWNS.size() * COUNTS.size() * SEEDS.size(), rows.size());

        for (long seed : SEEDS) {
            double failFastShort = recovery(rows, "fail-fast", 2 * SECOND, seed);
            double failFastLong = recovery(rows, "fail-fast", 60 * SECOND, seed);
            assertTrue(failFastLong < 0.25 * failFastShort,
                    "a cooldown longer than the recovery window collapses fail-fast recovery, seed " + seed
                            + ": 2s=" + failFastShort + " 60s=" + failFastLong);

            double retryOnlyShort = recovery(rows, "retry-only", 2 * SECOND, seed);
            double retryOnlyLong = recovery(rows, "retry-only", 60 * SECOND, seed);
            assertTrue(retryOnlyLong > 0.7 * retryOnlyShort,
                    "retry-only recovery is cooldown-insensitive, seed " + seed);
        }
    }

    @Test
    void writesHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        List<CooldownRow> rows = CooldownSweep.run(
                CanonicalExperiment.scenario(), COOLDOWNS, COUNTS, SEEDS);
        Path file = dir.resolve("cooldown.csv");
        CooldownSweep.writeCsv(file, rows);
        List<String> lines = Files.readAllLines(file);
        assertEquals("breaker,cooldown_s,client_count,seed,recovery_goodput,recovery_instability",
                lines.get(0));
        assertEquals(rows.size() + 1, lines.size());
    }

    private static double recovery(List<CooldownRow> rows, String breaker, long cooldownMicros, long seed) {
        return rows.stream()
                .filter(r -> r.breaker().equals(breaker) && r.cooldownMicros() == cooldownMicros
                        && r.clientCount() == 100 && r.seed() == seed)
                .findFirst()
                .orElseThrow()
                .recoveryGoodputPerSecond();
    }
}
