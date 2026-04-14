package core.solver.algorithm.searcher;

import java.util.*;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

/**
 * Best-First Search (BFS) algorithm implementation.
 * By configuring the Frontier's sorting strategy and the Predictor's heuristic function,
 * this class dynamically behaves as A*, Dijkstra's, or Greedy Best-First search.
 *
 * Condition: f(n) = g(n) + h(n).
 * When h(n) ≡ 0, it behaves as Dijkstra's algorithm.
 * When g(n) = 0, it behaves as Greedy Best-First.
 */
public final class BestFirstSearcher extends AbstractSearcher {

	private final Predictor predictor;

	/**
	 * @param frontier  Priority queue defining the expansion strategy.
	 * @param predictor Heuristic function estimator.
	 */
	public BestFirstSearcher(Frontier frontier, Predictor predictor) {
		super(frontier);
		this.predictor = predictor;
	}

	@Override
	public Deque<Node> search(Problem problem) {
		// Fast-fail if the problem instance is mathematically unsolvable
		if (!problem.solvable()) {
			return null;
		}

		// Initialize state configurations for a new search
		frontier.clear();
		explored.clear();
		nodesExpanded = 0;
		nodesGenerated = 0;

		Node root = problem.root(predictor);
		frontier.offer(root);

		// Core search loop
		while (true) {
			if (frontier.isEmpty()) {
				return null; // State space exhausted without reaching goal
			}

			Node node = frontier.poll();

			// Goal test upon expansion
			if (problem.goal(node.getState())) {
				return generatePath(node);
			}

			explored.add(node.getState());

			// Node expansion phase
			for (Node child : problem.childNodes(node, predictor)) {
				nodesGenerated++;
				// Prune states that have already been fully explored (Closed Set)
				if (!isExplored(child)) {
					frontier.offer(child);
				}
			}
			nodesExpanded++;
		}
	}
}