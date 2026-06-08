# Benchmark Report

## 1. Evaluation Goal

This benchmark evaluates two types of optimizations in the 15-Puzzle solver:

1. Algorithmic pruning: Manhattan, Linear Conflict, and 6-6-3 Disjoint PDB.
2. System-level optimization: object-oriented board representation vs. 64-bit Bitboard.

The goal is to distinguish two sources of speedup:

- PDB reduces the search tree size.
- Bitboard reduces the per-node transition cost.

## 2. Environment

- JDK: OpenJDK / Amazon Corretto 19.0.2
- Build tool: Maven
- JMH version: 1.36
- Threads: 1
- Warmup: 3 iterations, 1 second each
- Measurement: 5 iterations, 1 second each
- Macro benchmark timeout: 60 seconds per instance

## 3. Macro Benchmark: 100 Hard 15-Puzzle Instances

The raw CSV contains 3 trials. The formal summary below averages Trial 2 and Trial 3, while Trial 1 is retained in the CSV as a recorded warmup/check run.
The CSV was regenerated after replacing the old pairwise Linear Conflict implementation with the LIS-based implementation.

| Configuration | Success Rate | Mean Time (ms) | StdDev (ms) | Mean Expanded Nodes | Mean EBF |
|---|---:|---:|---:|---:|---:|
| IDA* + Manhattan | 100.00% | 2375.8 | 5511.7 | 21,132,738 | 1.341 |
| IDA* + Linear Conflict | 100.00% | 1249.0 | 2202.2 | 2,346,679 | 1.281 |
| IDA* + PDB (OOP) | 100.00% | 121.0 | 243.7 | 592,957 | 1.233 |
| IDA* + PDB (Bitboard) | 100.00% | 20.1 | 39.6 | 592,957 | 1.233 |

Validation checks on the regenerated CSV:

- all 300 trial-instance cases solved under all four configurations;
- zero solution-length mismatches across Manhattan, Linear Conflict, PDB OOP, and PDB Bitboard;
- PDB OOP and PDB Bitboard have identical generated-node and expanded-node counts for every case;
- the old pairwise Linear Conflict CSV is preserved as `benchmark_results/Search_results_legacy_pairwise_lc.csv`.

## 4. Micro Benchmark: State Transition

Reference result:

| Operation | Throughput |
|---|---:|
| Traditional `int[]` clone/swap | 106,581 ops/ms |
| 64-bit Bitboard transition | 541,549 ops/ms |

Speedup: 541,549 / 106,581 ≈ 5.08x.

Multi-fork GC verification was also run with 5 forks, 5 warmup iterations, 10 measurement iterations, and `-prof gc`.
The CSV output is stored at `benchmark_results/jmh_state_transition_multifork.csv`.

| Operation | Throughput | Allocation | GC Count |
|---|---:|---:|---:|
| Traditional `int[]` clone/swap | 107.405 ops/us | 80.000 B/op | 763 |
| 64-bit Bitboard transition | 706.134 ops/us | 0.000001 B/op | 0 |

Multi-fork throughput speedup: 706.134 / 107.405 ≈ 6.57x.
For summary reporting, this is stated as a stable 5x-plus state-transition advantage instead of a single best-case number.

## 5. Micro Benchmark: PDB Lookup

| Operation | Latency |
|---|---:|
| `HashMap<Long, Byte>` lookup | 184 ns/op |
| 1D `byte[]` lookup | 77 ns/op |

Speedup: 184 / 77 ≈ 2.38x.

## 6. Methodological Notes

To reduce benchmark bias, the evaluation uses:

- global JIT warmup before formal measurements;
- randomized state pool in JMH to reduce constant folding and dead-code elimination;
- repeated trials for macro-level evaluation;
- success-rate filtering under a 60-second timeout.

An earlier benchmark showed around 11x speedup, but it was later treated as a likely fixed-input / JIT benchmark artifact risk rather than a stable solver-level result.
After redesigning the benchmark with randomized runtime inputs, the reference speedup is around 5.08x.
The later 5-fork GC run reported about 6.57x throughput speedup, near-zero heap allocation in the bitboard state-transition path, and 80 B/op for the `int[]` clone/swap path.

## 7. Interpretation

PDB and Bitboard improve different levels of the solver.

- PDB reduces the number of expanded nodes by producing a stronger admissible heuristic.
- Bitboard keeps the search tree unchanged but reduces the cost of each state transition.
- Therefore, the final speedup comes from combining heuristic pruning with a state-transition hot path that has near-zero heap allocation in the measured JMH/GC profile.


Allocation boundary: the near-zero allocation claim is limited to the state-transition hot path measured by the JMH benchmark and GC profiler. It should not be read as a claim that every component of the full IDA* solver or benchmark harness performs zero allocation.

## PDB Lookup Benchmark Boundary

The `HashMap` lookup benchmark uses representative keys sampled from the 1024-state random-walk pool, rather than a full in-memory `HashMap` representation of the entire PDB. It is intended to compare lookup overhead under controlled access patterns, not to model the full memory footprint of a production HashMap-based PDB.
