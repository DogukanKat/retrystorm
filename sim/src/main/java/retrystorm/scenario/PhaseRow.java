package retrystorm.scenario;

/** One policy at one baseline utilisation and seed, summarised over the run's windows. */
public record PhaseRow(
        String policy,
        double utilisation,
        long seed,
        double baselineGoodputPerSecond,
        double recoveryGoodputPerSecond,
        double recoveryInstability) {
}
