package core.runner;

import core.problem.Problem;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.EvaluationType;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import npuzzle.solver.IdAStar;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 为搜索算法提供各样素材。包括
 *    问题实例列表
 *    使用的Frontier，
 *    使用的启发式函数 Predictor，
 *
 */
public abstract class EngineFeeder {
    /**
     * 根据存放问题输入样例的文本文件的内容，生成问题实例列表
     * @param problemLines  字符串数组，存放的是：问题输入样例的文本文件的内容
     * @return
     */
    public abstract ArrayList<Problem> getProblems(ArrayList<String> problemLines);

    /**
     * 生成采取某种估值机制的Frontier；与问题无关，
     *
     * @param type 结点评估器的类型
     * @return 使用评估机制的一个Frontier实例
     */
    public abstract Frontier getFrontier(EvaluationType type);

    /**
     * 获得对状态进行估值的Predictor；不同问题有不同的估值函数
     *
     * @param type 不同问题的估值函数的类型
     * @return 启发函数
     */
    public abstract Predictor getPredictor(HeuristicType type);

    /**
     * 用来做对比实验的IdAStar （Iterative Deepening AStar，迭代加深的AStar）
     */

    // 在EngineFeeder.java中修改getIdaStar方法
    public final AbstractSearcher getIdaStar(HeuristicType type) {
        Predictor predictor = getPredictor(type);
        Frontier frontier = getFrontier(EvaluationType.FULL);
        return new IdAStar(frontier, predictor); // 传入predictor
    }

    /**
     * 用来做对比实验的AStar, 对所有问题都是一样的
     * 可配置使用不同的启发函数
     * @param type 可配置的启发函数类型
     */
    public final AbstractSearcher getAStar(HeuristicType type) {
        Predictor predictor = getPredictor(type);
        // 获取Frontier，其Node以g(n)+h(n)的升序排列，相同时，按照g(n)的升序排列
        Frontier frontier = getFrontier(EvaluationType.FULL);
        // 根据frontier和predictor生成AStar引擎
        return new BestFirstSearcher(frontier, predictor);
    }

    /**
     * 用来做对比实验的Dijkstra，对所有的问题都是一样的
     *
     * @return Dijkstra搜索算法
     */
    public final AbstractSearcher getDijkstra() {
        // 获取Frontier，其Node以g(n)的升序排列
        Frontier frontier = getFrontier(EvaluationType.PATH_COST);
        // predictor：h(n)≡0，即Dijkstra算法
        return new BestFirstSearcher(frontier, (state, goal) -> 0);
    }

    /**
     * 获取使用模式数据库的IDA*搜索器（第三阶段专用）
     */
    /*public final AbstractSearcher getIdaStarWithPatternDB() {
        try {
            // 加载模式数据库
            stud.g01.solver.database.DisjointPatternDatabase patternDB =
                    stud.g01.solver.database.DisjointPatternDatabase.loadCompactDatabases();

            Frontier frontier = getFrontier(EvaluationType.FULL);
            return new stud.g01.solver.IdAStar(frontier, patternDB);

        } catch (Exception e) {
            System.err.println("模式数据库加载失败: " + e.getMessage());
            System.out.println("回退到曼哈顿距离IDA*");
            return getIdaStar(HeuristicType.MANHATTAN);
        }
    }*/


    /**
     * 获取使用空位距离的A*搜索器
     * 注意：需要在子类中实现EmptyDistancePredictor
     */
    public AbstractSearcher getEmptyDistanceAStar(HeuristicType type) {
        // 默认实现，子类可以重写
        System.out.println("空位距离启发式未实现，使用曼哈顿距离替代");
        return getAStar(type);
    }

    /**
     * 根据算法类型和启发式类型获取搜索器
     */
    public final AbstractSearcher getSearcher(String algorithmType, HeuristicType heuristicType) {
        switch (algorithmType.toUpperCase()) {
            case "ASTAR":
                return getAStar(heuristicType);
            case "IDASTAR":
                return getIdaStar(heuristicType);
            case "OPTIMIZED_ASTAR":
                return getAStar(heuristicType);
            case "EMPTY_DISTANCE_ASTAR":
                return getEmptyDistanceAStar(heuristicType);
            case "DIJKSTRA":
                return getDijkstra();
            default:
                System.out.println("未知算法类型: " + algorithmType + "，使用默认A*");
                return getAStar(heuristicType);
        }
    }

    /**
     * 性能测试：运行算法并返回详细结果
     */
    public final Map<String, Object> runAlgorithmTest(Problem problem, String algorithmType,
                                                      HeuristicType heuristicType) {
        Map<String, Object> results = new HashMap<>();

        AbstractSearcher searcher = getSearcher(algorithmType, heuristicType);

        long startTime = System.currentTimeMillis();
        Deque<Node> path = searcher.search(problem);
        long endTime = System.currentTimeMillis();

        results.put("algorithm", algorithmType);
        results.put("heuristic", heuristicType);
        results.put("timeMs", endTime - startTime);
        results.put("pathLength", path != null ? path.size() - 1 : -1);
        results.put("nodesGenerated", searcher.nodesGenerated());
        results.put("nodesExpanded", searcher.nodesExpanded());
        results.put("success", path != null);
        results.put("searcher", searcher);

        return results;
    }
}
