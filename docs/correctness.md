# Correctness Validation

## 1. Why Correctness Matters

For 15-Puzzle, performance alone is insufficient.
A solver must also guarantee that the returned solution path is legal and optimal.

This project validates correctness from three levels:

1. solvability check;
2. path replay validation;
3. solution-length consistency across admissible heuristics.

## 2. Solvability Check

Each input board is checked using the standard inversion-parity rule before search.
Unsolvable boards are rejected before running IDA*.

## 3. Path Replay Validation

For each solved instance, the solver exports a sequence of moves.
A replay checker applies the sequence to the initial board and verifies:

- each move is legal;
- the final board equals the goal board;
- the number of replayed moves equals the reported solution depth.

## 4. Solution-Length Consistency

The same instance is solved under multiple admissible heuristics:

- IDA* + Manhattan
- IDA* + Linear Conflict
- IDA* + 6-6-3 Disjoint PDB

Since all heuristics are admissible, IDA* should return the same optimal solution depth.
The PDB/Bitboard implementation is considered valid only when its returned depth is consistent with the baseline solvers.

## Replay Checker

A lightweight replay checker is provided at:

```text
scripts/replay_solution.py
```

It validates the exported files produced by the Java solver:

```text
bin/problem.txt
bin/solutionAnimation.txt
```

The checker reads the initial and goal boards from `problem.txt`, replays the action sequence from `solutionAnimation.txt`, and verifies that the final board equals the target state.

Example:

```bash
python scripts/replay_solution.py --problem bin/problem.txt --actions bin/solutionAnimation.txt --write-trace examples/latest_validated_trace.txt
```

Example output:

```text
PASS: 52 actions replayed; final board matches the goal state.
```

The optional `--write-trace` argument exports the replayed board states, which can be used for manual inspection or visualization debugging.

## 5. PDB Admissibility

The 15 non-blank tiles are partitioned into three disjoint subsets: 6-6-3.
Each pattern database stores the exact cost of solving one tile subset in the abstract state space.

Because the subsets are disjoint, the sum of the three pattern costs is a lower bound of the full puzzle cost.
Therefore, the 6-6-3 PDB heuristic remains admissible.