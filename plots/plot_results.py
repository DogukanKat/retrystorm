#!/usr/bin/env python3
import argparse
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
from matplotlib.patches import Patch
import pandas as pd

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_RESULTS = REPO_ROOT / "results"
DEFAULT_DOCS = REPO_ROOT / "docs"

FIG_SIZE = (12, 6)
FIG_DPI = 100
OVERLOAD_SHADE = {"color": "grey", "alpha": 0.15}

# Collapsing policies share a warm hue family, recovering ones a cool family,
# no-retry a neutral grey. Distinct dashes and staggered markers keep them
# separable even where they overlap at zero, and in grayscale print.
POLICY_STYLE = {
    "fixed-retry": dict(color="#a50026", linestyle="-", marker="o", markevery=(0, 5)),
    "exponential-backoff": dict(color="#f46d43", linestyle="--", marker="s", markevery=(2, 5)),
    "backoff-jitter": dict(color="#fdae61", linestyle="-.", marker="^", markevery=(4, 5)),
    "circuit-breaker": dict(color="#313695", linestyle="-", marker="D", markevery=(1, 5)),
    "token-bucket": dict(color="#1a9850", linestyle="--", marker="v", markevery=(3, 5)),
    "no-retry": dict(color="#666666", linestyle=":", marker="", markevery=None),
}
# Legend/plot order tells the story: collapsing, then recovering, then reference.
POLICY_ORDER = ["fixed-retry", "exponential-backoff", "backoff-jitter",
                "circuit-breaker", "token-bucket", "no-retry"]

# retry-only is the canonical circuit breaker, so it keeps its colour; fail-fast
# is a distinct warm hue with its own dash and marker.
BREAKER_STYLE = {
    "retry-only": dict(color="#313695", linestyle="-", marker="D"),
    "fail-fast": dict(color="#d73027", linestyle="--", marker="o"),
}
BREAKER_ORDER = ["retry-only", "fail-fast"]

# Phase map keeps the same policy colours: no-retry grey, retry-only the
# circuit-breaker blue, fail-fast the warm red.
PHASE_STYLE = {
    "no-retry": dict(color="#666666", linestyle=":", marker="s"),
    "retry-only": dict(color="#313695", linestyle="-", marker="D"),
    "fail-fast": dict(color="#d73027", linestyle="--", marker="o"),
}
PHASE_ORDER = ["no-retry", "retry-only", "fail-fast"]

# Baseline-vs-utilisation headline chart: fixed-retry takes the collapse (warm)
# hue, the breakers take cool hues, no-retry stays grey. All from the series
# palette; fail-fast is shown cool here to let the collapsing retry line stand out.
BASELINE_STYLE = {
    "no-retry": dict(color="#666666", linestyle=":", marker="s"),
    "retry-only": dict(color="#313695", linestyle="-", marker="D"),
    "fail-fast": dict(color="#1a9850", linestyle="-", marker="^"),
    "fixed-retry": dict(color="#a50026", linestyle="--", marker="o"),
}
BASELINE_ORDER = ["no-retry", "retry-only", "fail-fast", "fixed-retry"]


def save(fig, out_dir: Path, name: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    # metadata={"Date": None} keeps the PNG byte-stable across runs.
    fig.savefig(out_dir / name, dpi=FIG_DPI, metadata={"Date": None})
    plt.close(fig)


def series_over_time(df, column: str, ylabel: str, title: str, args, capacity=None,
                     legend_outside=False):
    fig, ax = plt.subplots(figsize=FIG_SIZE)
    for policy in POLICY_ORDER:
        if policy not in df["policy"].values:
            continue
        sub = df[df["policy"] == policy].sort_values("time_s")
        ax.plot(sub["time_s"], sub[column], label=policy, linewidth=1.6, markersize=5,
                **POLICY_STYLE[policy])
    if capacity is not None:
        ax.axhline(capacity, color="#999999", linestyle=(0, (1, 3)), linewidth=1,
                   label=f"server capacity ({int(capacity)}/s)")
    ax.axvspan(args.overload_start, args.overload_end, **OVERLOAD_SHADE)
    ax.set_xlabel("time (s)")
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    legend_title = "policy (collapse → recover → reference)"
    if legend_outside:
        ax.legend(title=legend_title, loc="upper left", bbox_to_anchor=(1.01, 1.0))
        fig.subplots_adjust(right=0.72)
    else:
        ax.legend(title=legend_title)
    return fig


def plot_canonical(args) -> None:
    df = pd.read_csv(args.combined)
    save(series_over_time(df, "goodput", "goodput (successes/s)",
                          "Goodput over time by retry policy", args, capacity=args.capacity),
         args.out_dir, "goodput_over_time.png")
    save(series_over_time(df, "p99_latency_ms", "p99 latency (ms)",
                          "p99 latency over time by retry policy", args, legend_outside=True),
         args.out_dir, "p99_over_time.png")


def plot_herd(args) -> None:
    df = pd.read_csv(args.herd)
    df["cv"] = df["recovery_instability"] / df["recovery_goodput"]
    agg = df.groupby(["breaker", "client_count"])["cv"].mean().reset_index()
    counts = sorted(agg["client_count"].unique())

    fig, ax = plt.subplots(figsize=FIG_SIZE)
    for breaker in BREAKER_ORDER:
        if breaker not in agg["breaker"].values:
            continue
        sub = agg[agg["breaker"] == breaker].sort_values("client_count")
        ax.plot(sub["client_count"], sub["cv"], label=breaker,
                linewidth=1.6, markersize=8, **BREAKER_STYLE[breaker])
    ax.set_xscale("log")
    ax.set_xticks(counts)
    formatter = mticker.ScalarFormatter()
    formatter.set_scientific(False)
    ax.xaxis.set_major_formatter(formatter)
    ax.xaxis.set_minor_locator(mticker.NullLocator())
    ax.set_ylim(bottom=0)
    ax.set_xlabel("client count")
    ax.set_ylabel("recovery instability (coefficient of variation)")
    ax.set_title("Recovery instability vs client count by breaker")
    ax.legend()
    save(fig, args.out_dir, "herd_instability.png")


def plot_phase(args) -> None:
    df = pd.read_csv(args.phase)
    df["cv"] = df["recovery_instability"] / df["recovery_goodput"]
    agg = df.groupby(["policy", "utilisation"]).agg(
        baseline=("baseline_goodput", "mean"),
        recovery=("recovery_goodput", "mean"),
        cv=("cv", "mean"),
    ).reset_index()

    fig, ax = plt.subplots(figsize=FIG_SIZE)
    for policy in PHASE_ORDER:
        sub = agg[agg["policy"] == policy].sort_values("utilisation")
        ax.plot(sub["utilisation"], sub["cv"], label=policy,
                linewidth=1.6, markersize=8, **PHASE_STYLE[policy])
    ax.set_ylim(bottom=0)
    ax.set_xticks(sorted(agg["utilisation"].unique()))
    ax.set_xlabel("baseline utilisation")
    ax.set_ylabel("recovery instability (coefficient of variation)")
    ax.set_title("Recovery instability vs baseline utilisation by policy")
    ax.legend()
    save(fig, args.out_dir, "phase_instability.png")

    at = agg[agg["utilisation"] == args.utilisation].set_index("policy").reindex(PHASE_ORDER)
    positions = range(len(PHASE_ORDER))
    width = 0.38
    colors = [PHASE_STYLE[policy]["color"] for policy in PHASE_ORDER]

    fig, ax = plt.subplots(figsize=FIG_SIZE)
    ax.bar([p - width / 2 for p in positions], at["baseline"], width, color=colors)
    ax.bar([p + width / 2 for p in positions], at["recovery"], width, color=colors,
           hatch="////", edgecolor="white")
    ax.set_xticks(list(positions))
    ax.set_xticklabels(PHASE_ORDER)
    ax.set_ylabel("goodput (successes/s)")
    ax.set_title(f"Baseline and recovery goodput at {args.utilisation:g} utilisation")
    ax.legend(handles=[Patch(facecolor="#888888", label="baseline"),
                       Patch(facecolor="#888888", hatch="////", edgecolor="white", label="recovery")])
    save(fig, args.out_dir, "phase_collapse.png")

    fig, ax = plt.subplots(figsize=FIG_SIZE)
    for policy in BASELINE_ORDER:
        sub = agg[agg["policy"] == policy].sort_values("utilisation")
        ax.plot(sub["utilisation"], sub["baseline"], label=policy,
                linewidth=1.6, markersize=8, **BASELINE_STYLE[policy])
    ax.set_ylim(bottom=0)
    ax.set_xticks(sorted(agg["utilisation"].unique()))
    ax.set_xlabel("baseline utilisation")
    ax.set_ylabel("baseline goodput (successes/s)")
    ax.set_title("Baseline goodput vs baseline utilisation by policy")
    ax.legend()
    save(fig, args.out_dir, "baseline_vs_utilisation.png")


def plot_open_trace(args) -> None:
    df = pd.read_csv(args.open_trace)
    sub = df[df["seed"] == args.seed].sort_values("time_s")

    fig, ax = plt.subplots(figsize=FIG_SIZE)
    ax.plot(sub["time_s"], sub["open_count"], color="#333333", linewidth=1.2)
    ax.axvspan(args.overload_start, args.overload_end, **OVERLOAD_SHADE)
    ax.set_ylim(bottom=0)
    ax.set_xlabel("time (s)")
    ax.set_ylabel("open breakers")
    ax.set_title(f"Open fail-fast breakers over time (100 clients, seed {args.seed})")
    save(fig, args.out_dir, "open_breakers_trace.png")


def main() -> None:
    parser = argparse.ArgumentParser(description="Render retrystorm result charts.")
    sub = parser.add_subparsers(dest="command", required=True)

    canonical = sub.add_parser("canonical", help="goodput and p99 over time from the combined CSV")
    canonical.add_argument("--combined", type=Path, default=DEFAULT_RESULTS / "combined.csv")
    canonical.add_argument("--out-dir", type=Path, default=DEFAULT_DOCS)
    canonical.add_argument("--overload-start", type=float, default=20.0)
    canonical.add_argument("--overload-end", type=float, default=30.0)
    canonical.add_argument("--capacity", type=float, default=1000.0,
                           help="server capacity (req/s) drawn as a reference line")
    canonical.set_defaults(func=plot_canonical)

    herd = sub.add_parser("herd", help="recovery instability vs client count from the herd CSV")
    herd.add_argument("--herd", type=Path, default=DEFAULT_RESULTS / "herd.csv")
    herd.add_argument("--out-dir", type=Path, default=DEFAULT_DOCS)
    herd.set_defaults(func=plot_herd)

    phase = sub.add_parser("phase", help="instability and collapse charts from the phase CSV")
    phase.add_argument("--phase", type=Path, default=DEFAULT_RESULTS / "phase.csv")
    phase.add_argument("--out-dir", type=Path, default=DEFAULT_DOCS)
    phase.add_argument("--utilisation", type=float, default=0.9,
                       help="utilisation shown in the collapse bar chart")
    phase.set_defaults(func=plot_phase)

    open_trace = sub.add_parser("open-trace", help="open-breaker count over time from the open-trace CSV")
    open_trace.add_argument("--open-trace", type=Path, default=DEFAULT_RESULTS / "open_trace.csv")
    open_trace.add_argument("--out-dir", type=Path, default=DEFAULT_DOCS)
    open_trace.add_argument("--seed", type=int, default=42, help="seed whose trace is drawn")
    open_trace.add_argument("--overload-start", type=float, default=20.0)
    open_trace.add_argument("--overload-end", type=float, default=30.0)
    open_trace.set_defaults(func=plot_open_trace)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
