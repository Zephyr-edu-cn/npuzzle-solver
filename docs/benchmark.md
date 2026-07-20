# Benchmark Report

## 1. Questions

The benchmark separates three effects in the canonical 15-Puzzle solver:

1. How much do stronger admissible heuristics reduce the search tree?
2. How much does replacing the generic immutable OOP path with an in-place, incremental-index path reduce solver time?
3. After those changes are controlled, how much additional benefit comes from a packed 64-bit representation over an in-place `int[]`?

## 2. Macro Benchmark Protocol

- Runtime: JetBrains Runtime OpenJDK 21.0.10; project bytecode target is Java 19.
- Operating system: Windows NT 10.0.26200.0.
- Threads: one solver task at a time.
- Dataset: `datasets/my_benchmark_100.txt`, 100 unique solvable instances.
- Dataset SHA-256: `EECDB98717EB0463CBDDCE3B93AE588AE2FF4D7A736C6D622D395CBD9A24F731`.
- Repetitions: 5 measured trials per configuration and instance.
- Timeout: 120 seconds per search.
- Warmup: all configurations over the first 5 instances before measurement.
- Timer: `System.nanoTime()` immediately around `search()` inside the worker task.
- Execution order: cyclic counterbalancing over trial and instance indices.
- Summary unit: median search time for each instance across five trials.
- Speedup summary: geometric mean and median of paired per-instance ratios.

The timer includes solver initialization performed inside `search()`, threshold iterations, and solution-path construction. It excludes task submission and `Future.get()` waiting before the task starts. The timeout mechanism remains outside the measured interval.

The dataset is repository-specific, not the official Korf 100 set. The file is fixed and hashed, but the original random seed, walk length, and selection metadata were not preserved. Results should therefore be described as a fixed self-built 100-instance benchmark.

## 3. Solver Configurations

| Configuration | State/update path | PDB index update |
|---|---|---|
| IDA* + Manhattan | Immutable OOP | Not applicable |
| IDA* + Linear Conflict | Immutable OOP | Not applicable |
| IDA* + PDB OOP | Immutable `PuzzleBoard` objects | Recomputed from the board |
| IDA* + PDB Mutable Array | In-place `int[]` swap/backtrack | Incremental |
| IDA* + PDB Bitboard | Packed `long` transition | Incremental |

The three PDB configurations share the same IDA* threshold logic, move order, immediate-reversal pruning, PDB tables, actual-state goal test, and node-count definitions. Mutable Array is the strong representation baseline. PDB OOP remains a generic implementation baseline rather than a pure array-vs-long comparison.

## 4. Results

Raw data: `benchmark_results/Search_results_v2.csv`.

CSV SHA-256: `B21FD0A46528204A154977E733321CE4A9615AE6364777FF8FB1FC8D33F68DAA`.

| Configuration | Complete instances | Mean of per-instance medians (ms) | Median (ms) | Mean expanded | Mean EBF |
|---|---:|---:|---:|---:|---:|
| IDA* + Manhattan | 100/100 | 2659.761 | 377.087 | 21,132,738 | 1.342 |
| IDA* + Linear Conflict | 100/100 | 1328.253 | 269.930 | 2,346,679 | 1.281 |
| IDA* + PDB OOP | 100/100 | 138.628 | 21.863 | 592,957 | 1.233 |
| IDA* + PDB Mutable Array | 100/100 | 24.231 | 4.539 | 592,957 | 1.233 |
| IDA* + PDB Bitboard | 100/100 | 19.421 | 3.848 | 592,957 | 1.233 |

PDB reduces the mean number of expanded nodes by `21,132,738 / 592,957 = 35.64x` relative to Manhattan.

Paired search-time speedups:

| Comparison | Geometric mean | Median ratio | Interpretation |
|---|---:|---:|---|
| PDB OOP to Mutable Array | 4.944x | 5.173x | Combined in-place state update and incremental PDB-index maintenance |
| Mutable Array to Bitboard | 1.218x | 1.225x | Additional packed-representation benefit under the same incremental algorithm |
| PDB OOP to Bitboard | 6.023x | 6.386x | Combined specialized-path benefit; not a pure Bitboard attribution |
## 5. Independent Data Checks

A separate CSV audit, independent of the runner summary, found:

- 2,500 rows: 5 trials x 100 instances x 5 configurations;
- 2,500 `Solved` rows and no timeout, OOM, or error;
- each of the 25 configuration/execution-position cells appears exactly 100 times;
- zero solution-length mismatches across 500 trial-instance groups;
- zero generated-node or expanded-node mismatches across the three PDB paths.

The node equality is evidence that Mutable Array and Bitboard preserve the PDB OOP search tree. Equal solution depth is supporting cross-validation; the optimality argument still depends on IDA* threshold semantics and admissible heuristics.

## 6. JMH State-Transition Benchmark

The JMH benchmark uses a 1024-state random-walk pool, a fixed generation seed, runtime-dependent indexing, and a `Blackhole`. It compares an `int[]` clone/swap transition with a packed-long transition.

| Run | `int[]` clone/swap | Packed `long` | Ratio |
|---|---:|---:|---:|
| Conservative reference | 106,581 ops/ms | 541,549 ops/ms | 5.08x |
| Five-fork GC profile | 107.405 ops/us | 706.134 ops/us | 6.57x |

GC profile:

| Operation | Allocation | GC count |
|---|---:|---:|
| `int[]` clone/swap | 80.000 B/op | 763 |
| Packed `long` transition | 0.000001 B/op | 0 |

The two recorded reference ratios are **5.08x** in the conservative run and **6.57x** in the five-fork GC-profile run. Both refer only to this narrow transition microbenchmark; neither compares Bitboard against the in-place Mutable Array solver or substitutes for the macro comparison.

## 7. PDB Lookup Microbenchmark

| Operation | Latency |
|---|---:|
| Representative `HashMap<Long, Byte>` lookup | 184 ns/op |
| Direct 1D `byte[]` lookup | 77 ns/op |

The HashMap setup contains representative keys from the random-walk state pool rather than a full in-memory HashMap copy of the PDB. The result compares lookup overhead under the measured access pattern, not total production memory footprint. No hardware-counter result for L1/L2 cache hit rates was collected.

## 8. Correction History and Evidence Boundaries

- An early fixed-input transition benchmark reported about 11x. It was withdrawn because the input design exposed JIT artifact risks. The project does not claim assembly-level proof of a specific C2 optimization.
- `benchmark_results/Search_results_legacy_pairwise_lc.csv` preserves results from the inadmissible pairwise Linear Conflict implementation.
- `benchmark_results/Search_results.csv` is a later four-configuration run with corrected Linear Conflict, but its macro timer started after task submission, used millisecond resolution, and kept a fixed configuration order. Its exact timing values are legacy evidence only.
- `benchmark_results/Search_results_v2.csv` is the current macro benchmark evidence.
- Its five macro trials run in one warmed JVM. Counterbalancing and per-instance medians reduce within-process order/noise effects, but they are not cross-process fork evidence.
- `benchmark_results/jmh_state_transition_multifork.csv` is the current five-fork GC-profile evidence.

Allocation claims are limited to the transition operation measured by JMH. The complete solver still allocates during setup and solution-path construction.

## 9. Reproduction

Build and run the macro benchmark:

```bash
mvn clean package
java -cp target/classes benchmark.SearchBenchmarkRunner
```

Useful overrides:

```text
-Dnpuzzle.benchmark.input=datasets/my_benchmark_100.txt
-Dnpuzzle.benchmark.trials=5
-Dnpuzzle.benchmark.timeoutSeconds=120
-Dnpuzzle.benchmark.maxInstances=100
-Dnpuzzle.benchmark.output=benchmark_results/Search_results_v2.csv
```

Run the five-fork transition profile:

```bash
java -jar target/benchmarks.jar StateTransitionBenchmark \
  -wi 5 -i 10 -f 5 -tu us -prof gc \
  -rf csv -rff benchmark_results/jmh_state_transition_multifork.csv
```