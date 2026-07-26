package retrystorm.analysis;

/** How many of the fail-fast breakers are open at one 100 ms sample of one seed's run. */
public record OpenTraceRow(
        long seed,
        double timeSeconds,
        int openCount) {
}
