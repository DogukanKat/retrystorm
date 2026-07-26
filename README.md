# retrystorm

A discrete-event simulation of retry storms: how retries, backoff, jitter, token buckets and circuit breakers change the way a system recovers from overload.

## Why

A short overload makes some requests fail. Clients retry them, and those retries pile on top of the load that caused the failure.

So the system can stay down long after the original trigger is gone. It has two stable states, healthy and collapsed, and a brief shock is enough to push it into the second one. Usually the only way out is to shed load by hand.

This is called metastable failure. Background reading:

- Marc Brooker's blog: https://brooker.co.za/blog/
- "Metastable Failures in Distributed Systems", HotOS'21: https://sigops.org/s/conferences/hotos/2021/papers/hotos21-s11-bronson.pdf

## What it simulates

Clients and servers are generic. Nothing in the model is tied to a specific domain.

You wire them into a topology in code, not in a config file: fan-in, fan-out or chains. Each client sends requests at some rate and has its own retry policy. Each server has a fixed capacity and a bounded queue, and rejects new work once the queue is full.

Each run records goodput, offered load, queue depth and p99 latency. That is what makes the policies comparable under the same overload.

## Status

Work in progress.

- **Done:** the engine (`Event`, `Simulator`). Integer microsecond clock, one seeded `Random`, and events at the same instant run in the order they were scheduled.
- **Done:** the server side (`Request`, `Server`, `ExponentialServiceTime`). Fixed worker count, bounded FIFO queue, and work is rejected once the queue is full.
- **Done:** the client side (`Client`, `RateSchedule`). Poisson arrivals, a timeout on every attempt, and a hard attempt cap.
- **Done:** all six policies — `NoRetry`, `FixedRetry`, `ExponentialBackoff`, `ExponentialBackoffWithJitter`, `TokenBucketRetry` and `CircuitBreaker`. Stateful ones recover through `onSuccess`/`onFailure`, which the client reports as requests settle.
- **Done:** metrics collection and CSV output (`MetricsCollector`, `CsvWriter`). Per-second buckets track offered load, goodput, rejections, timeouts, retries, queue depth and success-latency percentiles.
- **Done:** the scenario runner and the canonical overload experiment (`Scenario`, `ScenarioRunner`, `CanonicalExperiment`). `retrystorm.Main` runs all six policies against the same scenario and seed and writes one CSV per policy plus a combined CSV to `results/`.
- **Missing:** the plotting script and a written-up chart. The collapse-versus-recovery result is visible in the CSVs but not yet drawn.

## Planned experiments

Same topology, same load, one policy at a time:

- **No retry** — the baseline.
- **Fixed retry** — retry N times, same delay every time.
- **Exponential backoff** — the delay doubles on each attempt.
- **Backoff with jitter** — random delay, so clients stop retrying in lockstep.
- **Token bucket** — a retry budget that runs out if failures keep coming.
- **Circuit breaker** — stop retrying after too many failures, then probe to see if it is safe again.

## Layout

- `sim/` — the Java 21 simulation (Gradle project)
- `plots/` — Python plotting scripts
- `results/` — CSV output, git-ignored

## Running

Run the canonical experiment. It writes one CSV per policy plus `combined.csv` into `results/`:

```bash
cd sim && ./gradlew run
```

Re-run it across several seeds and write a per-seed summary to `results/validation.csv`, to check the result is not a fluke of one seed:

```bash
cd sim && ./gradlew run --args="validate"
```

Sweep the client count (total load) for backoff-with-jitter and the token bucket, writing `results/sweep.csv`. This shows where backoff stops being enough: it recovers with few clients and collapses with many.

```bash
cd sim && ./gradlew run --args="sweep"
```

Tests:

```bash
cd sim && ./gradlew test
```

Plots. `plot_results.py` is still a stub and raises `NotImplementedError`, and there is no CSV to plot yet. You can install the dependencies already:

```bash
cd plots && pip install -r requirements.txt
```
