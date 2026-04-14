package npuzzle.queue;

import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Map;

/**
 * Priority queue implementation with lazy deletion strategy.
 * Utilizes a HashMap to track the most optimal node for each state,
 * avoiding the O(N) overhead of PriorityQueue.remove() when updating node costs.
 */
public class PqFrontier implements Frontier {
    private final PriorityQueue<Node> priorityQueue;
    private final Map<State, Node> stateMap;
    private final Comparator<Node> evaluator;

    public PqFrontier(Comparator<Node> evaluator) {
        this.evaluator = evaluator;
        this.priorityQueue = new PriorityQueue<>(100000, evaluator);
        this.stateMap = new HashMap<>(100000);
    }

    @Override
    public Node poll() {
        // Lazy deletion logic: continuously poll until the retrieved node
        // matches the best-known instance in the state map.
        while (!priorityQueue.isEmpty()) {
            Node node = priorityQueue.poll();
            Node bestKnown = stateMap.get(node.getState());

            // Use reference equality (==) for fast identity check
            if (bestKnown == node) {
                stateMap.remove(node.getState());
                return node;
            }
        }
        return null;
    }

    @Override
    public boolean offer(Node node) {
        State state = node.getState();
        Node existing = stateMap.get(state);

        if (existing == null) {
            priorityQueue.offer(node);
            stateMap.put(state, node);
            return true;
        } else {
            // If the new node has a better evaluation, add it to the queue
            // and update the map, leaving the old node to be lazily deleted later.
            if (evaluator.compare(node, existing) < 0) {
                priorityQueue.offer(node);
                stateMap.put(state, node);
                return true;
            }
            return false;
        }
    }

    @Override
    public void clear() {
        priorityQueue.clear();
        stateMap.clear();
    }

    @Override
    public int size() {
        return stateMap.size(); // Returns the number of valid, non-obsolete nodes
    }

    @Override
    public boolean isEmpty() {
        return stateMap.isEmpty();
    }

    @Override
    public boolean contains(Node node) {
        return stateMap.containsKey(node.getState());
    }
}