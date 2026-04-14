package core.solver.queue;

/**
 * Defines the evaluation strategy for state-space search node ordering.
 */
public enum EvaluationType {
    FULL,       // f(n) = g(n) + h(n) [A* Search / IDA*]
    PATH_COST,  // f(n) = g(n)        [Dijkstra's / Uniform Cost Search]
    HEURISTIC   // f(n) = h(n)        [Greedy Best-First Search]
}