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

    /**
     * Whether a new request may be sent at all. Fail-fast policies shed load
     * here when they are tripped; by default every request is admitted.
     */
    default boolean admit(Simulator sim) {
        return true;
    }

    /** Reports that a request finally succeeded. Stateful budgets recover here. */
    default void onSuccess() {
    }

    /** Reports a failed attempt, before {@link #decide} is consulted for it. */
    default void onFailure() {
    }
}
