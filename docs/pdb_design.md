# 6-6-3 Disjoint Pattern Database Design

## 1. Scope and Partition

The bundled database is for the canonical 4x4 goal `[1, 2, ..., 15, 0]`. It is not goal-independent; runtime use with another goal fails fast.

The non-blank tiles are partitioned as:

- PDB 1: `{1, 5, 6, 9, 10, 13}`
- PDB 2: `{7, 8, 11, 12, 14, 15}`
- PDB 3: `{2, 3, 4}`

The runtime heuristic is `h(s) = h1(s) + h2(s) + h3(s)`.

## 2. Abstract State and Cost Partitioning

A generator state contains the positions of the current pattern tiles plus the blank position. The blank is needed to determine which swaps are legal, but blank movement is not charged independently in every PDB.

For each reverse transition:

- blank swaps with a pattern tile: cost 1 and the pattern encoding changes;
- blank swaps with a non-pattern tile: cost 0 and the pattern encoding is unchanged.

The generator uses standard 0-1 BFS over `(pattern encoding, blank position)`: a 0-cost relaxation is added to the front of a primitive deque, a 1-cost relaxation is added to the back, and a state is settled when removed at minimum distance. Consequently, each physical tile move is charged only to the database containing that tile. The sum of the three abstract distances is therefore admissible.

## 3. Compact Index

For a `k`-tile pattern, each tile position occupies four bits. A 6-tile index uses 24 bits and a 3-tile index uses 12 bits. The index is used directly in a flat `byte[]`:

- each 6-tile table: `2^24 = 16,777,216` bytes;
- 3-tile table: `2^12 = 4,096` bytes;
- total payload: 32 MiB + 4 KiB.

Many bit patterns encode duplicate tile positions and are unreachable; those entries remain fallback zero values. Legal solver states always produce distinct positions and therefore address generated entries.

At runtime, OOP reconstructs the three indices by scanning the board. Mutable Array and Bitboard carry the indices through DFS and update only the moved tile's four-bit position field.

## 4. Generation and File Validation

Reverse generation covers every legal ordered placement:

| Pattern | Legal encodings | Maximum cost layer |
|---|---:|---:|
| `{1,5,6,9,10,13}` | 5,765,760 | 28 |
| `{7,8,11,12,14,15}` | 5,765,760 | 26 |
| `{2,3,4}` | 3,360 | 15 |

`stateCount` is incremented when an encoding is first settled, including the goal encoding. This produces the exact legal-encoding counts shown above. Regeneration with the standard relaxation-based 0-1 BFS produced byte-for-byte identical cost payloads to the previously bundled tables.

Payload SHA-256 values after regeneration:

- PDB 1: `FD373C43963355D5D6CBD7615C3EBA55E950D39B76E4496DC20171CF1D3385A2`
- PDB 2: `54C3CD8099383864F091A7BC4E1850CAF17CD90884B32EED45FB18185F080D78`
- PDB 3: `3D8AA3794F6183A507494CD021441D9B4FC3C1B09100DDE5920ADEEBE177E98A`

The loader validates:

- board size 4;
- exact ordered subset;
- expected permutation count `P(16, k)`;
- complete payload length;
- absence of trailing bytes.

## 5. Storage Trade-off

Direct indexing intentionally spends memory to remove hashing, boxing, collision handling, and key storage from the lookup path. The repository's PDB lookup microbenchmark measures lower latency for representative direct-array lookups than for representative `HashMap<Long, Byte>` lookups. No claim is made about a measured hardware cache-hit rate.

For a larger puzzle or a larger pattern, `16^k`-style direct indexing may become impractical. The representation and cost type would need to be reconsidered rather than assumed to scale unchanged.
