package retrystorm.scenario;

/** One breaker variant at one cooldown, client count and seed, summarised over the recovery window. */
public record CooldownRow(
        String breaker,
        long cooldownMicros,
        int clientCount,
        long seed,
        double recoveryGoodputPerSecond,
        double recoveryInstability) {
}
