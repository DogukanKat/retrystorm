package retrystorm.sim;

/**
 * Terminal fate of a request, once no further attempt will be made.
 *
 * <p>A request that never succeeds is labelled by how its final attempt
 * failed, whether it stopped because the retry policy declined or because the
 * attempt cap was reached. Why it stopped is answered by the attempt count.
 */
public enum Outcome {
    SUCCESS,
    REJECTED,
    TIMED_OUT
}
