package retrystorm.policy;

import retrystorm.engine.Simulator;

/** Decides whether a failed attempt is retried, and after how long. */
public interface RetryPolicy {

    /**
     * @param attempt     how many attempts the request has already made, at least 1
     * @param failureKind how the attempt that just finished failed
     * @param sim         engine, for the clock and the shared random source
     */
    RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim);

    /** Reports that a request finally succeeded. Stateful budgets recover here. */
    default void onSuccess() {
    }

    /** Reports a failed attempt, before {@link #decide} is consulted for it. */
    default void onFailure() {
    }
}
