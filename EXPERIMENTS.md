# Experiments

Every run is deterministic: all randomness comes from the engine's seeded
`Random`, so a given seed reproduces byte-identical CSVs. Run from `sim/`;
output goes to `results/`.

## Canonical scenario

Shared by every experiment below.

| Parameter | Value |
| --- | --- |
| Workers | 10 |
| Mean service time | 10 ms |
| Server capacity | 1000 req/s (workers ÷ mean service time) |
| Queue bound | 100 |
| Baseline arrival rate | 600 req/s |
| Overload arrival rate | 2500 req/s |
| Overload window | 20 s – 30 s |
| Run length | 60 s |
| Metric bucket | 1 s |
| Per-attempt timeout | 50 ms |
| Attempt cap | 6 |
| Default seed | 42 |

Retry policies (shared retry delay 25 ms, max backoff 1 s, max 5 retries):

| Policy | Parameters |
| --- | --- |
| `no-retry` | — |
| `fixed-retry` | 5 retries, 25 ms delay |
| `exponential-backoff` | 5 retries, 25 ms base, 1 s cap |
| `backoff-jitter` | 5 retries, 25 ms base, 1 s cap, full jitter |
| `token-bucket` | 5 retries, 25 ms delay, capacity 100, cost 1/retry, refill 0.1/success |
| `circuit-breaker` | 5 retries, 25 ms delay, window 50, trip at 0.5 failure rate, 2 s cooldown |

## Per-policy time series

```bash
cd sim && ./gradlew run
```

Output: `results/<policy>.csv` for each policy, plus `results/combined.csv`.
Columns: `policy,time_s,offered,goodput,rejections,timeouts,retries,queue_depth,p50_latency_ms,p99_latency_ms`.
One row per 1 s bucket; latency fields are empty when a bucket has no successes.
Shows each policy's per-second metrics across the run, seed 42.

## Multi-seed validation

```bash
cd sim && ./gradlew run --args="validate"
```

Output: `results/validation.csv`.
Columns: `policy,seed,baseline_goodput,overload_goodput,recovery_goodput,overload_retries`.
Seeds: 42, 43, 44, 45, 46. Windows: baseline 0–20 s, overload 20–30 s, recovery 30–60 s.
Shows mean goodput per window and overload-window retry totals for every policy on every seed.

## Client-count sweep

```bash
cd sim && ./gradlew run --args="sweep"
```

Output: `results/sweep.csv`.
Columns: `client_count,policy,baseline_goodput,recovery_goodput`.
Client counts: 25, 50, 75, 100, where 100 is the canonical load. Offered load per client
is held fixed (6 req/s baseline, 25 req/s overload per client), so total load scales with
the count; server capacity stays at 1000 req/s. Policies: `backoff-jitter`, `token-bucket`.
Recovery window 30–60 s, seed 42.
Shows recovery-window goodput for the two policies as the client count varies.

## Distributed circuit-breaker herd

```bash
cd sim && ./gradlew run --args="herd"
```

Output: `results/herd.csv`.
Columns: `breaker,client_count,seed,recovery_goodput,recovery_instability`.
Runs a retry-only breaker and a fail-fast breaker, each split across
1, 10, 50, 100, 200 independent clients at fixed total load, over seeds 42–46.
Server capacity 1000 req/s; breaker window 50, trip at 0.5, 2 s cooldown.
Recovery instability is the standard deviation of per-second goodput in the
30–60 s window. Shows recovery goodput and its instability per breaker, client
count and seed.

## Baseline-utilisation phase map

```bash
cd sim && ./gradlew run --args="phase"
```

Output: `results/phase.csv`.
Columns: `policy,utilisation,seed,baseline_goodput,recovery_goodput,recovery_instability`.
Sweeps baseline utilisation 0.5, 0.6, 0.7, 0.8, 0.9 (baseline arrival rate as a
fraction of the 1000 req/s capacity) with the overload spike held at 2500 req/s,
over seeds 42–46, at 100 independent clients. Policies: `no-retry`, `retry-only`
breaker, `fail-fast` breaker. Baseline window 0–20 s, recovery window 30–60 s.
Shows each policy's baseline and recovery goodput and recovery instability as the
baseline load approaches capacity.

## Recovery-window spike analysis

```bash
cd sim && ./gradlew run --args="analyze-spikes"
```

Output: `results/spike_analysis.csv`.
Columns: `policy,time_s,successes,multi_attempt,p99_all_ms,p99_first_ms,p99_multi_ms`.
Reruns the recovering policies (no-retry, token-bucket, circuit-breaker) on the
canonical scenario and, for each recovery bucket, splits success latencies at the
50 ms per-attempt timeout: under it a success completed on its first attempt, at
or above it the request timed out and completed on a retry (exact while recovery
rejections are ~0). Reports p99 over all successes, first-attempt only, and
multi-attempt only, per bucket. Empty p99 fields mean that subset had no
successes in the bucket.
Demonstrated that the recovery-window p99 spikes are produced by the ~1% of
successes that complete on a retry (timeout + backoff + a second service round,
about 2-2.7x a first-attempt latency); first-attempt-only p99 matches no-retry
exactly.

## Baseline breaker-tripping analysis

```bash
cd sim && ./gradlew run --args="analyze-breaker-state"
```

Output: `results/breaker_state.csv`.
Columns: `breaker,utilisation,seed,baseline_tripped_fraction`.
Reruns the retry-only and fail-fast breakers with 100 independent clients at
utilisations 0.5, 0.6, 0.7, 0.8, 0.9 over seeds 42-46, and samples every
breaker's open/half-open state every 10 ms across the baseline window (0-20 s,
before any overload). Reports the fraction of breaker-samples spent tripped.
Demonstrated that below 0.9 utilisation both breakers stay closed for the whole
baseline (0%), while at 0.9 they trip in steady state (fail-fast ~30%, retry-only
~73%), accounting for the depressed baseline goodput at high utilisation.


