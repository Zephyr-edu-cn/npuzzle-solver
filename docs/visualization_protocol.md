# Visualization Protocol

This document describes the file-based interface between the Java N-Puzzle solver and the C++/SFML visualization frontend.

After solving a puzzle, the Java solver exports three files under the `bin/` directory:

```text
bin/problem.txt
bin/solution.txt
bin/solutionAnimation.txt
```

These files transfer puzzle metadata, search-tree information, and the final action sequence to the visualization frontend.

## 1. Export Pipeline

```text
Java Solver
    -> bin/problem.txt
    -> bin/solution.txt
    -> bin/solutionAnimation.txt
    -> C++/SFML Visualization Frontend
    -> solution-path replay and rendering
```

## 2. Exported Files

### 2.1 `problem.txt`

`problem.txt` stores the puzzle size, the initial state, and the goal state.

Format:

```text
<size> <initial_state...> <goal_state...>
```

Example for a 4x4 puzzle:

```text
4 1 2 3 4 5 6 7 8 9 10 11 12 13 14 0 15 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 0
```

Here `0` represents the blank tile.

### 2.2 `solution.txt`

`solution.txt` stores generated search-tree nodes for visualization and inspection.

Format for each node:

```text
<size> <parent_id> <g> <h> <f> <state...>
```

Field meanings:

- `size`: puzzle width/height;
- `parent_id`: parent node index in the exported search tree;
- `g`: path cost from the initial state;
- `h`: heuristic estimate;
- `f`: evaluation value, usually `g + h`;
- `state`: flattened board state.

For very large search trees, this file may be skipped to avoid excessive output size.

### 2.3 `solutionAnimation.txt`

`solutionAnimation.txt` stores the final action sequence for path animation.

Example:

```text
RIGHT UP LEFT DOWN
```

Supported actions:

```text
UP DOWN LEFT RIGHT
```

Each token represents one legal move of the blank tile.

## 3. Correctness Usage

The exported `problem.txt` and `solutionAnimation.txt` can also be used for replay-based correctness validation.

The replay checker:

1. reads the initial and goal states from `problem.txt`;
2. reads the action sequence from `solutionAnimation.txt`;
3. applies each action to the blank tile;
4. verifies that every move is legal;
5. verifies that the final board equals the goal state;
6. verifies that the number of applied actions equals the replayed solution length.

This makes the visualization export useful for both presentation and debugging.

## 4. Integration Boundary

This repository focuses on:

- the Java search engine;
- file export for visualization;
- benchmark evaluation;
- correctness validation.

The C++/SFML frontend consumes the exported files and reconstructs the solution path for rendering.

The Java solver and the visualization frontend are connected through files rather than direct runtime coupling.