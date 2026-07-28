package retrystorm.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HerdExperimentTest {

    private static final List<Integer> COUNTS = List.of(1, 100);
    private static final List<Long> SEEDS = List.of(42L, 43L);

    @Test
    void failFastBreakerIsFarLessStableThanRetryOnlyWhenDistributed() {
        List<HerdRow> rows = HerdExperiment.run(CanonicalExperiment.scenario(), COUNTS, SEEDS);
        assertEquals(3 * COUNTS.size() * SEEDS.size(), rows.size());

        for (long seed : SEEDS) {
            double failFastMany = instability(rows, "fail-fast", 100, seed);
            double retryOnlyMany = instability(rows, "retry-only", 100, seed);
            assertTrue(failFastMany > 2 * retryOnlyMany,
                    "seed " + seed + ": fail-fast instability " + failFastMany
                            + " should dwarf retry-only " + retryOnlyMany + " at 100 clients");
        }
    }

    @Test
    void cooldownJitterLowersFailFastInstabilityAtManyClients() {
        List<HerdRow> rows = HerdExperiment.run(CanonicalExperiment.scenario(), COUNTS, SEEDS);
        for (long seed : SEEDS) {
            double failFast = coefficientOfVariation(rows, "fail-fast", 100, seed);
            double jittered = coefficientOfVariation(rows, "jittered-fail-fast", 100, seed);
            assertTrue(jittered < failFast,
                    "cooldown jitter lowers fail-fast recovery instability at 100 clients, seed " + seed
                            + ": fail-fast=" + failFast + " jittered=" + jittered);
        }
    }

    @Test
    void retryOnlyBreakerStaysStableRegardlessOfClientCount() {
        List<HerdRow> rows = HerdExperiment.run(CanonicalExperiment.scenario(), COUNTS, SEEDS);
        for (long seed : SEEDS) {
            assertTrue(instability(rows, "retry-only", 1, seed) < 60,
                    "retry-only should be steady with one client, seed " + seed);
            assertTrue(instability(rows, "retry-only", 100, seed) < 60,
                    "retry-only should stay steady with many clients, seed " + seed);
        }
    }

    @Test
    void writesHeaderAndOneRowPerEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("herd.csv");
        HerdExperiment.writeCsv(file, List.of(
                new HerdRow("retry-only", 100, 42, 601.7, 32.9),
                new HerdRow("fail-fast", 100, 42, 562.8, 138.1)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertEquals("breaker,client_count,seed,recovery_goodput,recovery_instability", lines.get(0));
        assertEquals("retry-only,100,42,601.700,32.900", lines.get(1));
        assertEquals("fail-fast,100,42,562.800,138.100", lines.get(2));
    }

    private static double instability(List<HerdRow> rows, String breaker, int clientCount, long seed) {
        return find(rows, breaker, clientCount, seed).recoveryInstability();
    }

    private static double coefficientOfVariation(List<HerdRow> rows, String breaker, int clientCount, long seed) {
        HerdRow row = find(rows, breaker, clientCount, seed);
        return row.recoveryInstability() / row.recoveryGoodputPerSecond();
    }

    private static HerdRow find(List<HerdRow> rows, String breaker, int clientCount, long seed) {
        return rows.stream()
                .filter(row -> row.breaker().equals(breaker)
                        && row.clientCount() == clientCount && row.seed() == seed)
                .findFirst()
                .orElseThrow();
    }
}
