package retrystorm.policy;

import retrystorm.engine.Simulator;

/** Never retries. The control case every other policy is measured against. */
public final class NoRetry implements RetryPolicy {

    @Override
    public RetryDecision decide(int attempt, FailureKind failureKind, Simulator sim) {
        return RetryDecision.giveUp();
    }
}
