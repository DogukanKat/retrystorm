package retrystorm.scenario;

/** One policy/seed run summarised by mean goodput in each window and its overload retries. */
public record ValidationRow(
        String policy,
        long seed,
        double baselineGoodputPerSecond,
        double overloadGoodputPerSecond,
        double recoveryGoodputPerSecond,
        long overloadRetries) {
}
