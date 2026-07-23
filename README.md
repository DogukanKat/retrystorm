# retrystorm

A discrete-event simulation of retry storms — how naive retries, backoff, jitter, token buckets and circuit breakers affect recovery from overload in distributed systems.

## Why

Retry storms are a classic way for distributed systems to fall over and *stay* fallen over. A transient overload — a brief traffic spike, a slow dependency, a blip of packet loss — causes some requests to fail. Clients retry those failures, and the retries pile on top of the load that is already there, so the system stays saturated even after the original trigger is long gone. This is a **metastable failure**: the system has a stable healthy state and a stable collapsed state, and a short shock can knock it from the first into the second, where it remains until load is forcibly shed. See Marc Brooker's writing (https://brooker.co.za/blog/) and the HotOS'21 paper "Metastable Failures in Distributed Systems" (https://sigops.org/s/conferences/hotos/2021/papers/hotos21-s11-bronson.pdf) for the background that inspired this project.

## What it simulates

Generic clients and servers are connected in configurable topologies — fan-in, fan-out, and chains — defined entirely in a scenario file rather than in code. Each client issues requests at some offered rate and, on failure or timeout, consults a pluggable retry policy to decide whether and when to retry; each server has a finite capacity and queue and sheds load once saturated. As the simulation runs it tracks goodput, total offered load, queue depth, and p99 latency, so different retry strategies can be compared under the same overload.

## Status

Work in progress — simulation core under development. The Java project builds and runs; the entry point (`retrystorm.Main`) currently only validates its scenario argument, and the engine, servers, clients, policies and metrics are not implemented yet.

## Planned experiments

Retry policies to compare, holding topology and load fixed:

- **No-retry baseline** — clients never retry; the control case.
- **Naive N-retry** — a fixed number of immediate retries per request.
- **Exponential backoff** — retry delay grows exponentially with attempts.
- **Backoff + jitter** — exponential backoff with randomization to de-synchronize retries.
- **Token bucket / adaptive** — a retry budget that throttles retries under sustained failure.
- **Circuit breaker** — stop retrying (and often stop sending) once a failure threshold is crossed, then probe for recovery.

## Layout

- `sim/` — Java 21 discrete-event simulation (Gradle project).
- `plots/` — Python plotting scripts and their dependencies.
- `results/` — simulation output (CSV); generated, git-ignored.

## Running

Simulation (Java / Gradle):

```bash
cd sim && ./gradlew run --args="path/to/scenario.yaml"
```

Plots (Python — not implemented yet):

```bash
cd plots
pip install -r requirements.txt && python plot_results.py results/example.csv
```
