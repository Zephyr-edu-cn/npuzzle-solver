package core.solver.algorithm.heuristic;

/**
 * Enumeration of available heuristic evaluation functions.
 */
public enum HeuristicType {
    // N-Puzzle Heuristics
    MISPLACED,        // Number of misplaced tiles
    MANHATTAN,        // Sum of Manhattan distances
    HAMMING,          // Hamming distance
    DISJOINT_PATTERN, // Statically pre-computed Disjoint Pattern Database
    LINEAR_CONFLICT,  // Manhattan distance augmented with linear conflict penalties

    // Pathfinding Heuristics (8-directional)
    PF_EUCLID,        // Euclidean distance
    PF_MANHATTAN,     // Manhattan distance (inadmissible for 8-way movement)
    PF_GRID,          // Diagonal-first grid distance

    // Special Problem Relaxations
    MC_HARMONY        // Missionaries and Cannibals relaxed constraints
}