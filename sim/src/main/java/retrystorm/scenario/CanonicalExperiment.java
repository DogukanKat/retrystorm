package retrystorm.scenario;

import java.util.List;
import java.util.function.Supplier;

import retrystorm.policy.CircuitBreaker;
import retrystorm.policy.ExponentialBackoff;
import retrystorm.policy.ExponentialBackoffWithJitter;
import retrystorm.policy.FixedRetry;
import retrystorm.policy.NoRetry;
import retrystorm.policy.RetryPolicy;
import retrystorm.policy.TokenBucketRetry;

/**
 * The canonical experiment: a server at moderate utilization, a transient
 * arrival spike from t1 to t2, and observation to t3 well after it ends. Each
 * policy runs against the same scenario and seed so their curves are
 * comparable.
 */
public final class CanonicalExperiment {

    private static final long SECOND = 1_000_000;
    private static final long MILLIS = 1_000;

    private static final long RETRY_DELAY_MICROS = 25 * MILLIS;
    private static final long MAX_BACKOFF_MICROS = SECOND;
    private static final int MAX_RETRIES = 5;

    /** A named policy plus a factory, since stateful policies need a fresh instance per run. */
    public record NamedPolicy(String name, Supplier<RetryPolicy> factory) {
    }

    private CanonicalExperiment() {
    }

    public static Scenario scenario() {
        return new Scenario(
                42L,
                60 * SECOND,
                SECOND,
                10,
                10 * MILLIS,
                100,
                600.0,
                2_500.0,
                20 * SECOND,
                30 * SECOND,
                50 * MILLIS,
                6);
    }

    public static List<NamedPolicy> policies() {
        return List.of(
                new NamedPolicy("no-retry", NoRetry::new),
                new NamedPolicy("fixed-retry", () -> new FixedRetry(MAX_RETRIES, RETRY_DELAY_MICROS)),
                new NamedPolicy("exponential-backoff",
                        () -> new ExponentialBackoff(MAX_RETRIES, RETRY_DELAY_MICROS, MAX_BACKOFF_MICROS)),
                new NamedPolicy("backoff-jitter",
                        () -> new ExponentialBackoffWithJitter(MAX_RETRIES, RETRY_DELAY_MICROS, MAX_BACKOFF_MICROS)),
                new NamedPolicy("token-bucket",
                        () -> new TokenBucketRetry(MAX_RETRIES, RETRY_DELAY_MICROS, 100.0, 1.0, 0.1)),
                new NamedPolicy("circuit-breaker",
                        () -> new CircuitBreaker(MAX_RETRIES, RETRY_DELAY_MICROS, 50, 0.5, 2 * SECOND)));
    }
}
