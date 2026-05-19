# Linear Conflict Fix Record

## 1. Problem

The previous Linear Conflict implementation counted every inverted tile pair as `+2`.
This is unsafe because the same tile can participate in several pairwise conflicts.
Counting all pairs can therefore charge the same tile more than once, producing a heuristic value above the true remaining cost.

That is an implementation bug, not a problem with the benchmark example.

## 2. Exposing Example

One benchmark instance exposed the issue:

```text
Manhattan solution length:            46
Old pairwise Linear Conflict length:  48
6-6-3 PDB solution length:            46
```

After the fix:

```text
Manhattan solution length:            46
Fixed Linear Conflict length:         46
6-6-3 PDB solution length:            46
Replay validation:                    PASS
```

The old result has been archived in `benchmark_results/Search_results_legacy_pairwise_lc.csv`.
The regenerated result is stored in `benchmark_results/Search_results.csv`.

## 3. Double-Counting Shape

The failing instance included row and column patterns where one tile appeared in multiple pairwise conflicts.

Row example:

```text
current row tiles:      [3, 4, 1]
target column order:   [2, 3, 0]
pairwise extra cost:   4
LIS removal cost:      2
```

Column example:

```text
current column tiles:  [14, 10, 6]
target row order:     [3, 2, 1]
pairwise extra cost:  6
LIS removal cost:     4
```

The pairwise implementation charged every inversion.
The admissible interpretation is to charge the minimum number of tiles that must leave the line to restore goal order.

## 4. Corrected Rule

For each row:

1. collect tiles whose target row is the current row;
2. convert them to their target column sequence;
3. compute the longest increasing subsequence;
4. charge `2 * (count - LIS length)`.

For each column, the same rule is applied to target row sequence.

This implements the "minimum removals from the line" interpretation and avoids charging the same tile repeatedly.

## 5. Validation

Validation after the fix:

- `mvn -q -DskipTests package` passes on JDK 19;
- the exposing instance now returns depth 46 for Manhattan, Linear Conflict, and 6-6-3 PDB;
- replay validation passes for the fixed solution path;
- exhaustive 8-puzzle sanity check over 181,440 reachable states found no overestimate for the LIS rule;
- the regenerated 15-puzzle macro benchmark has zero solution-length mismatches across 300 trial-instance cases;
- PDB OOP and PDB Bitboard still produce identical generated-node and expanded-node counts.

## 6. Conclusion

Linear Conflict is theoretically admissible, but the implementation must avoid duplicate conflict charging.
The old pairwise implementation violated that requirement.
The current LIS-based implementation restores the admissibility evidence chain and aligns Linear Conflict with Manhattan and PDB solution depths on the benchmark set.
