# Correctness Validation

## 1. Correctness Claims

For the supported problem scope, the solver must establish three different properties:

1. **Input validity and solvability**: the board is a valid permutation and lies in the same parity class as the goal.
2. **Path validity**: every returned move is legal and the final board equals the requested goal.
3. **Optimality**: IDA* uses admissible heuristics and increases its threshold to the minimum exceeded `f = g + h` value.

Replay and cross-heuristic agreement are engineering checks. They support the implementation evidence, but they do not replace the admissibility and IDA* arguments.

## 2. Input and Goal Handling

`NPuzzleProblem` validates the declared board size, tile-array length, tile range, uniqueness, and presence of the blank before search. Solvability is then checked with inversion parity relative to the supplied goal.

`IdAStar` handles the root before entering DFS. If the initial state already equals the goal, it returns a path containing only the root, corresponding to zero moves and zero expanded/generated nodes.

DFS goal detection compares the actual successor board with the goal. It does not use `h == 0` as a substitute for state equality.

The bundled 6-6-3 PDB was generated for the canonical 4x4 goal `[1, 2, ..., 15, 0]`. PDB search rejects any other goal before solvability or heuristic evaluation. Manhattan and Linear Conflict remain goal-relative; general goal-relative search is covered by a custom-goal regression test.

## 3. IDA* Optimality

The initial threshold is `h(start)`. Within an iteration, a node is expanded only when `g + h <= bound`. The DFS records the minimum `f` value that exceeded the current bound; the next iteration uses that value as its new bound.

With an admissible heuristic, `f` is a lower bound on the cost of every completion through that node. Therefore no solution cheaper than the current threshold is skipped, and the first goal reached in the first successful threshold iteration has optimal cost. Consistency improves search behavior but is not required for IDA* tree-search optimality.

The solver prunes only the immediate reverse of the previous move. It does not maintain a global closed set, so longer cycles can be revisited. This affects performance, not the optimality argument.

## 4. PDB Admissibility and Goal Scope

The non-blank tiles are split into three disjoint subsets. During reverse generation, the abstract state includes both the pattern encoding and the blank position:

- swapping the blank with a tile in the current pattern costs 1;
- swapping the blank with a non-pattern tile costs 0.

A physical tile move is therefore charged only to the PDB containing that tile. Summing the three abstract distances cannot charge one real move to multiple patterns, so the total is a lower bound on the full solution cost.

The loader verifies the board size, ordered subset, legal abstract-state count, payload length, and absence of trailing data. Regeneration covers 5,765,760 encodings for each 6-tile pattern and 3,360 for the 3-tile pattern. The regenerated payload hashes were identical to the previous payloads; only the off-by-one header count was corrected.
## 5. Linear Conflict

The old implementation added 2 for every inverted pair. A tile could participate in several pairs, so that implementation could double-charge required detours and overestimate.

The current implementation processes each row and column independently:

1. retain tiles whose goal belongs to the current line;
2. map them to target row/column order;
3. compute the longest increasing subsequence;
4. add `2 * (count - LIS length)`.

`count - LIS length` is the minimum number of tiles that must leave the line, and each such tile needs at least one move out and one move back. The exposing 15-Puzzle instance changed from an incorrect LC result of 48 moves to 46, matching Manhattan and PDB; the 46-move path passes independent replay.

`LinearConflictExhaustiveTest` now performs a repository-local exhaustive check over all 181,440 states reachable in the 8-Puzzle. It checks:

- admissibility: `h(state) <= exactDistance(state)`;
- consistency on every legal edge: `h(state) <= 1 + h(next)`.

## 6. Multi-Implementation Cross-Checks

The current formal CSV contains 5 trials x 100 instances x 5 configurations:

- all 2,500 rows are `Solved`;
- all five configurations have the same solution depth in each of 500 trial-instance groups;
- PDB OOP, PDB Mutable Array, and PDB Bitboard have identical generated and expanded node counts in every group.

The three PDB paths are independent searches. Node equality confirms that state updates and incremental indices preserve the same search tree; it is not produced by sharing counters.

## 7. Independent Replay

The Python checker reads the exported initial state, goal, and action sequence. It validates every move and compares the final board with the goal:

```bash
python scripts/replay_solution.py \
  --problem bin/problem.txt \
  --actions bin/solutionAnimation.txt
```

Bundled example output:

```text
PASS: 46 actions replayed; final board matches the goal state.
```

Replay proves that the reported action sequence is legal and reaches the goal. It does not by itself prove shortest-path optimality.

## 8. Automated Regression Suite

The Maven test suite covers:

- solved-root zero-move behavior;
- one-move and custom-goal search;
- PDB fail-fast behavior for noncanonical goals;
- OOP, Mutable Array, and Bitboard PDB path/node equivalence;
- invalid board rejection and parity checks;
- heuristic unit tests;
- exhaustive 8-Puzzle Linear Conflict admissibility and consistency;
- benchmark-order counterbalancing and EBF reconstruction.

Run with JDK 19 or later:

```bash
mvn test
```