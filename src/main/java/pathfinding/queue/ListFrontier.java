package pathfinding.queue;

import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * ��Ȼ���ܲ��ߣ���������������������⡣
 * ��ͬѧ�ǲ��ձ�д�Լ���Frontier�ࡣ
 *
 */
public class ListFrontier extends ArrayList<Node> implements Frontier {
    // �ڵ����ȼ��Ƚ�������Node���ж�����������ͬ�ıȽ�����
    //      Dijkstra,
    //      Greedy Best-First,
    //      Best-First
    // evaluator����������ȡ��Frontier�е��ĸ�Ԫ�ء�
    private final Comparator<Node> evaluator;

    public ListFrontier(Comparator<Node> evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public Node poll() {
        return super.remove(0);
    }

    /**
     *
     * @param node
     * @return
     */
    @Override
    public boolean contains(Node node) {
        return getNode(node.getState()) != null;
    }
    /**
     * �����node���뵽���ȶ����У�
     * ���Frontier���Ѿ�������node״̬��ͬ�Ľ�㣬�������������й�ֵ����Ľ��
     * @param node Ҫ�������ȶ��еĽ��
     * @return
     */
    @Override
    public boolean offer(Node node) {
        Node oldNode = getNode(node.getState());
        if (oldNode == null) { //frontier��δ�ҵ���node״̬��ͬ�Ľڵ�
            //����fֵ����ʱ��nodeӦ���ڵ�λ��
            super.add(getIndex(node), node);
            return true;
        } else { //node���ظ����ʵĽڵ�
            return discardOrReplace(oldNode, node);
        }
    }

    /**
     *
     * ���Frontier���Ѿ�������node״̬��ͬ�Ľ�㣬 ������������֮�䲻�õ���һ����
     *
     *
     * @param oldNode
     * @param node ��㣬��״̬Ҫô�Ѿ�������Explored�У�Ҫô�Ѿ�������Frontier��
     * @return true: replaced; false: discarded
     *
     */
    private boolean discardOrReplace(Node oldNode, Node node) {
        // ����ɽ��Ĺ�ֵ���µĴ󣬼������ɵĽ�����
        if (evaluator.compare(oldNode, node) > 0) {
            // ���½ڵ��滻�ɽڵ�
            replace(oldNode, node);
            return true;
        }
        return false;   //discard���ӵ��½��
    }

    private Node getNode(State state) {
        for (var node : this){
            if (node.getState().equals(state)){
                return node;
            }
        }
        return null;
    }

    private int getIndex(Node node) {
        int index = Collections.binarySearch(this, node, evaluator);
        if (index < 0) index = -(index + 1);
        return index;
    }


    /**
     * �ýڵ� e �滻��������ͬ״̬�ľɽڵ� oldNode ͬѧ�ǿ�����Ҫ��д���������
     *
     * @param oldNode ���滻�Ľ��
     * @param newNode �½��
     */
    private void replace(Node oldNode, Node newNode) {
        super.remove(oldNode);
        super.add(getIndex(newNode), newNode);
    }
}
