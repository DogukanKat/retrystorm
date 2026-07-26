package retrystorm.analysis;

/** Fraction of the baseline window one breaker kind spends tripped at a given utilisation and seed. */
public record BreakerStateRow(
        String breaker,
        double utilisation,
        long seed,
        double baselineTrippedFraction) {
}
