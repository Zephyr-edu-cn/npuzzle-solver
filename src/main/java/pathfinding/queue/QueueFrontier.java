package pathfinding.queue;

import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * ����BFS�Ķ���
 */
public class QueueFrontier extends ArrayDeque<Node> implements Queue<Node>, Frontier{
    @Override
    public boolean contains(Node node) {
        return super.contains(node);
    }
}