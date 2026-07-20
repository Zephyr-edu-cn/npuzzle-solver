# High-Performance N-Puzzle Solver

![Language](https://img.shields.io/badge/Language-Java_19-blue.svg)
[![Maven CI](https://github.com/Zephyr-edu-cn/npuzzle-solver/actions/workflows/maven-ci.yml/badge.svg)](https://github.com/Zephyr-edu-cn/npuzzle-solver/actions/workflows/maven-ci.yml)
![Benchmark](https://img.shields.io/badge/Benchmark-JMH-orange.svg)

A Java IDA* solver for the canonical 15-Puzzle. The project studies performance at two levels: reducing the search tree with stronger admissible heuristics, and reducing per-node overhead with specialized state representations.

<p align="center">
  <img src="./assets/visualization.gif" width="50%"/>
</p>

## Core Design

### Search and heuristics

- IDA* with threshold progression to the minimum `f = g + h` that exceeded the previous bound.
- Manhattan Distance, LIS-based Linear Conflict, and an additive 6-6-3 Disjoint Pattern Database.
- Reverse 0/1-cost PDB generation: moving a pattern tile costs 1; moving a non-pattern tile costs 0.
- Immediate-reversal pruning without a global closed set, preserving IDA*'s low-memory design.

### State representations

The PDB solver has three execution paths with the same move order, threshold logic, PDB tables, reverse-move pruning, goal test, and node counters:

1. **OOP**: immutable `PuzzleBoard` objects and full PDB-index reconstruction.
2. **Mutable Array**: one in-place `int[]` with swap/backtrack and incremental PDB indices.
3. **Bitboard**: a packed 64-bit board with bitwise transitions and incremental PDB indices.

The Mutable Array path is the strong baseline for isolating the additional contribution of packed representation. The OOP-to-Bitboard comparison measures the combined effect of avoiding object copies, avoiding full index reconstruction, and packing the board.

## Correctness Boundaries

- A solved root returns a zero-move path.
- Goal detection compares actual board states; it does not infer the goal from `h == 0`.
- General heuristics support goal-relative search.
- The bundled 6-6-3 PDB is goal-specific and accepts only the canonical goal `[1, 2, ..., 15, 0]`; other goals fail fast.
- Problem construction rejects invalid board sizes and tile permutations.
- `scripts/replay_solution.py` independently replays exported moves and checks the final state.
- An executable JUnit test exhaustively checks all 181,440 reachable 8-Puzzle states for Linear Conflict admissibility and consistency.

## Macro Benchmark

The formal runner uses the fixed repository dataset `datasets/my_benchmark_100.txt`:

- 100 unique solvable instances;
- 5 measured trials and 5 solver configurations;
- 120-second timeout per search;
- search timing inside the worker task with `System.nanoTime()`;
- cyclic counterbalancing so every configuration occupies every execution position;
- per-instance median time, followed by paired speedup statistics.

All 2,500 runs solved successfully. Across 500 trial-instance groups there were zero solution-length mismatches. OOP, Mutable Array, and Bitboard PDB paths had identical generated and expanded node counts in every group.

| Configuration | Mean of per-instance medians (ms) | Median (ms) | Mean expanded | Mean EBF |
|---|---:|---:|---:|---:|
| IDA* + Manhattan | 2659.761 | 377.087 | 21,132,738 | 1.342 |
| IDA* + Linear Conflict | 1328.253 | 269.930 | 2,346,679 | 1.281 |
| IDA* + PDB (OOP) | 138.628 | 21.863 | 592,957 | 1.233 |
| IDA* + PDB (Mutable Array) | 24.231 | 4.539 | 592,957 | 1.233 |
| IDA* + PDB (Bitboard) | **19.421** | **3.848** | **592,957** | **1.233** |

The PDB reduces mean expanded nodes by about **35.64x** relative to Manhattan.

<p align="center">
  <img src="./assets/search_scale_reduction.png" width="82%"/>
</p>

The figure uses one expanded-node value per configuration and unique instance after verifying that all five repeated trials had identical node counts.

Paired geometric-mean speedups are:

- PDB OOP to Mutable Array: **4.944x**
- Mutable Array to Bitboard: **1.218x**
- PDB OOP to Bitboard: **6.023x**

The final comparison is therefore a combined specialized-path gain, not a pure Bitboard gain. Raw data is in [`benchmark_results/Search_results_v2.csv`](benchmark_results/Search_results_v2.csv).

<p align="center">
  <img src="./assets/layered_performance_comparison.png" width="82%"/>
</p>

The first step is a combined in-place/incremental hot-path gain. The second step measures the additional packed-representation benefit under the matched incremental algorithm.

## JMH Microbenchmarks

The state-transition microbenchmark compares `int[]` clone/swap with a packed-long transition over a 1024-state random-walk pool and runtime-dependent indexing.

- Conservative reference: `541,549 / 106,581 = 5.08x` throughput.
- Five-fork GC profile: `706.134 / 107.405 = 6.57x` throughput.
- Allocation profile: approximately `80 B/op` for clone/swap and near `0 B/op` for the packed transition.

The public summary remains **stable 5x-plus** for this narrow clone/swap microbenchmark. It is not a claim that Bitboard is 5x faster than the in-place Mutable Array solver. The macro ablation above measures that stronger comparison separately.

The PDB lookup benchmark reports `184 ns/op` for representative `HashMap<Long, Byte>` lookups and `77 ns/op` for direct `byte[]` lookup. This supports the measured lookup-latency claim; no hardware-counter claim about a specific L1/L2 hit rate is made.

### Benchmark correction record

An early fixed-input transition benchmark reported about 11x. Because fixed inputs can expose constant-folding, dead-code-elimination, and other JIT artifact risks, that result was withdrawn. The current benchmark uses a state pool, runtime indexing, and a `Blackhole`. No assembly-level claim about a specific C2 optimization is made.

The earlier macro CSV also used millisecond timing started after task submission and a fixed configuration order. It is retained only as legacy evidence. The current runner measures `search()` inside the task with nanosecond resolution and counterbalances order.

## Documentation

- [Benchmark report](docs/benchmark.md)
- [Correctness validation](docs/correctness.md)
- [Linear Conflict fix record](docs/linear_conflict_fix.md)
- [6-6-3 PDB design](docs/pdb_design.md)
- [Visualization protocol](docs/visualization_protocol.md)

## Visualization

This repository provides the Java-side trace export and replay validation used with an external C++/SFML viewer:

- Viewer: https://github.com/BroMikey/Npuzzle-Visulization
- Protocol notes: [docs/visualization_protocol.md](docs/visualization_protocol.md)

## Build and Reproduction

Prerequisites: JDK 19 or later and Maven 3.x.

```bash
mvn clean package
```

Run the formal macro benchmark:

```bash
java -cp target/classes benchmark.SearchBenchmarkRunner
```

Optional properties include:

```text
-Dnpuzzle.benchmark.trials=5
-Dnpuzzle.benchmark.timeoutSeconds=120
-Dnpuzzle.benchmark.maxInstances=100
-Dnpuzzle.benchmark.output=benchmark_results/Search_results_v2.csv
```

Regenerate the README figures from the formal CSV (requires Pillow):

    python scripts/generate_readme_figures.py
Run JMH:

```bash
java -jar target/benchmarks.jar StateTransitionBenchmark
java -jar target/benchmarks.jar PdbLookupBenchmark
```

Regenerate the bundled PDB files:

```bash
java -Xmx2g -cp target/classes npuzzle.solver.database.PatternDatabaseGenerator
```

Replay an exported solution:

```bash
python scripts/replay_solution.py --problem bin/problem.txt --actions bin/solutionAnimation.txt
```

Expected output for the bundled example:

```text
PASS: 46 actions replayed; final board matches the goal state.
```

## Scope

This is an implementation, optimization, validation, and correction study of established search techniques. It does not claim a new search algorithm. The fixed 100-instance dataset is repository-specific rather than the official Korf 100 set; its file identity is recorded in the benchmark report, while its original seed and selection metadata are not available.
