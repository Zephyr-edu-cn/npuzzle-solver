package core.solver.algorithm.searcher;

import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import java.util.*;

/**
 * Base class for search algorithms.
 * Maintains the set of explored states and the frontier of candidate nodes.
 */
public abstract class AbstractSearcher {

    protected final Set<State> explored = new HashSet<>();
    protected final Frontier frontier;

    protected int nodesGenerated = 0;
    protected int nodesExpanded = 0;

    public AbstractSearcher(Frontier frontier) {
        this.frontier = frontier;
    }

    public int nodesGenerated() { return nodesGenerated; }
    public int nodesExpanded() { return nodesExpanded; }

    public abstract Deque<Node> search(Problem problem);

    protected Deque<Node> generatePath(Node goal) {
        Deque<Node> path = new ArrayDeque<>();
        Node current = goal;
        while (current != null) {
            path.push(current);
            current = current.getParent();
        }
        return path;
    }

    protected boolean isExplored(Node node) {
        return explored.contains(node.getState());
    }
}