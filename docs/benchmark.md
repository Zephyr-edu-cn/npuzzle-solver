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

Aggregated over 3 trials with a 60-second timeout.

| Configuration | Success Rate | Mean Time (ms) | StdDev (ms) | Mean Expanded Nodes | Mean EBF |
|---|---:|---:|---:|---:|---:|
| IDA* + Manhattan | 99.00% | 5566.3 | 10044.8 | 17,515,815 | 1.341 |
| IDA* + Linear Conflict | 100.00% | 2389.3 | 4224.2 | 2,069,172 | 1.278 |
| IDA* + PDB (OOP) | 100.00% | 256.7 | 526.7 | 592,956 | 1.233 |
| IDA* + PDB (Bitboard) | 100.00% | 47.9 | 97.6 | 592,956 | 1.233 |

## 4. Micro Benchmark: State Transition

| Operation | Throughput |
|---|---:|
| Traditional `int[]` clone/swap | 106,581 ops/ms |
| 64-bit Bitboard transition | 541,549 ops/ms |

Speedup: 541,549 / 106,581 ≈ 5.08x.

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

An earlier benchmark showed around 11x speedup, but it was later identified as a JVM JIT artifact.
After redesigning the benchmark with randomized runtime inputs, the stable speedup is around 5.08x.

## 7. Interpretation

PDB and Bitboard improve different levels of the solver.

- PDB reduces the number of expanded nodes by producing a stronger admissible heuristic.
- Bitboard keeps the search tree unchanged but reduces the cost of each state transition.
- Therefore, the final speedup comes from combining heuristic pruning with low-level zero-allocation implementation.
