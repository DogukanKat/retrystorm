package retrystorm.scenario;

/** One sweep point: a policy at a given client count, with its window goodput. */
public record SweepRow(
        int clientCount,
        String policy,
        double baselineGoodputPerSecond,
        double recoveryGoodputPerSecond) {
}
