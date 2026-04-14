package core.problem;

import core.solver.queue.Node;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 全局搜索树记录器，用于在搜索过程中捕获所有生成的结点。
 */
public final class SearchTreeRecorder {

    private static final Set<Node> recordedNodes = new LinkedHashSet<>();
    private static boolean recording = false;

    private SearchTreeRecorder() {
        // Utility class
    }

    public static synchronized void start() {
        recordedNodes.clear();
        recording = true;
    }

    public static synchronized List<Node> stopAndGetSnapshot() {
        recording = false;
        return new ArrayList<>(recordedNodes);
    }

    public static synchronized void stop() {
        recording = false;
    }

    public static synchronized void record(Node node) {
        if (!recording || node == null) {
            return;
        }
        recordedNodes.add(node);
    }
}

