# 6-6-3 Disjoint Pattern Database Design

## 1. Motivation

Manhattan Distance estimates each tile independently and ignores interactions between tiles.
Pattern Database stores exact costs for abstract subproblems, providing a stronger admissible heuristic.

## 2. Pattern Split

The 15 non-blank tiles are divided into three disjoint groups:

- Pattern 1: 6 tiles
- Pattern 2: 6 tiles
- Pattern 3: 3 tiles

The final heuristic is:

h(s) = h<small>1</small>(s) + h<small>2</small>(s) + h<small>3</small>(s)

## 3. Reverse BFS Generation

Each PDB is generated from the goal state by reverse BFS.
For each abstract state, the database records the minimum number of moves required to restore the corresponding tile subset.

## 4. Runtime Lookup

During IDA* search:

1. encode the current board into pattern indices;
2. query three precomputed databases;
3. sum the three values as the heuristic estimate.

## 5. Compact Storage

The optimized implementation stores PDB data in raw `byte[]` arrays instead of `HashMap<Long, Byte>`.

This improves:

- memory locality;
- lookup latency;
- GC behavior;
- cache friendliness.

## 6. Implementation Lessons

Earlier pattern database attempts exposed memory pressure and indexing consistency issues.
The final implementation uses 6-6-3 partitioning, compact indexing, and raw byte-array storage to balance heuristic strength and memory feasibility.

## 7. Additivity and Cost Partitioning

The 6-6-3 PDB is additive under the same cost-partitioned abstraction used by `PatternDatabaseGenerator`.

During reverse BFS generation, the blank position is tracked together with the pattern encoding. When the blank swaps with a tile that belongs to the current pattern, the transition has cost 1 and is pushed to the next cost layer. When the blank swaps with a non-pattern tile, the pattern encoding does not change, the transition has cost 0, and it remains in the current cost layer.

This means that a physical tile move is charged only to the pattern containing the moved tile. Non-pattern moves are zero-cost transitions for the current pattern, so the same physical move is not counted by multiple pattern databases. Therefore, summing the three disjoint PDB values remains admissible.

The blank is still part of the abstract state because it determines which swaps are legal, but blank repositioning is not independently charged across all patterns.
