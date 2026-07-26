package retrystorm.metrics;

import java.util.List;

/** One policy's labelled metric rows from a scenario run. */
public record RunResult(String policy, List<BucketRow> rows) {
}
