package retrystorm.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvWriterTest {

    private static BucketRow row(int index, long offered, long goodput, long p50Micros, long p99Micros) {
        return new BucketRow(index, (long) index * 1_000_000, offered, goodput, 0, 0, 0, 0, p50Micros, p99Micros);
    }

    @Test
    void writesHeaderThenOneRowPerBucket(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("no-retry.csv");
        CsvWriter.writeSingle(file, new RunResult("no-retry", List.of(
                row(0, 100, 90, 1_500, 9_000),
                row(1, 100, 95, 1_200, 8_000))));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertEquals(CsvWriter.HEADER, lines.get(0));
        assertEquals("no-retry,0.000,100,90,0,0,0,0,1.500,9.000", lines.get(1));
        assertEquals("no-retry,1.000,100,95,0,0,0,0,1.200,8.000", lines.get(2));
    }

    @Test
    void formatsMicrosecondsAsMillisecondsAndSecondsWithThreeDecimals(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("row.csv");
        CsvWriter.writeSingle(file, new RunResult("p", List.of(
                new BucketRow(2, 2_000_000, 7, 3, 4, 5, 6, 8, 1_234, 56_789))));
        String row = Files.readAllLines(file).get(1);
        assertEquals("p,2.000,7,3,4,5,6,8,1.234,56.789", row);
    }

    @Test
    void emitsEmptyLatencyFieldsWhenTheBucketHasNoSuccesses(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.csv");
        CsvWriter.writeSingle(file, new RunResult("p", List.of(
                new BucketRow(0, 0, 40, 0, 12, 28, 5, 100, 0, 0))));
        String row = Files.readAllLines(file).get(1);
        assertEquals("p,0.000,40,0,12,28,5,100,,", row);
    }

    @Test
    void writesLatencyWheneverThereIsAtLeastOneSuccess(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("one.csv");
        CsvWriter.writeSingle(file, new RunResult("p", List.of(
                new BucketRow(0, 0, 10, 1, 0, 0, 0, 0, 2_000, 2_000))));
        String row = Files.readAllLines(file).get(1);
        assertEquals("p,0.000,10,1,0,0,0,0,2.000,2.000", row);
    }

    @Test
    void combinedFileConcatenatesPoliciesUnderOneHeader(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("combined.csv");
        CsvWriter.writeCombined(file, List.of(
                new RunResult("a", List.of(row(0, 1, 1, 0, 0))),
                new RunResult("b", List.of(row(0, 2, 2, 0, 0)))));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertEquals(CsvWriter.HEADER, lines.get(0));
        assertTrue(lines.get(1).startsWith("a,"));
        assertTrue(lines.get(2).startsWith("b,"));
    }

    @Test
    void createsMissingParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/deeper/out.csv");
        CsvWriter.writeSingle(file, new RunResult("p", List.of(row(0, 1, 1, 0, 0))));
        assertTrue(Files.exists(file));
    }

    @Test
    void sameInputProducesByteIdenticalOutput(@TempDir Path dir) throws IOException {
        RunResult result = new RunResult("p", List.of(row(0, 100, 90, 1_500, 9_000)));
        Path first = dir.resolve("first.csv");
        Path second = dir.resolve("second.csv");
        CsvWriter.writeSingle(first, result);
        CsvWriter.writeSingle(second, result);
        assertEquals(-1L, Files.mismatch(first, second));
    }
}
