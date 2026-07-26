package retrystorm.scenario;

/** One breaker at one client count and seed, summarised by recovery goodput and its instability. */
public record HerdRow(
        String breaker,
        int clientCount,
        long seed,
        double recoveryGoodputPerSecond,
        double recoveryInstability) {
}
