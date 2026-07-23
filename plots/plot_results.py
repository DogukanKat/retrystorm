#!/usr/bin/env python3
import argparse


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plot retrystorm simulation results from a metrics CSV.",
    )
    parser.add_argument("csv_path", help="Path to the results CSV.")
    parser.add_argument("-o", "--output", default=None, help="Figure output path.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    raise NotImplementedError("plot_results is not implemented yet")


if __name__ == "__main__":
    main()
