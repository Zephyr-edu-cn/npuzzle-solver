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
