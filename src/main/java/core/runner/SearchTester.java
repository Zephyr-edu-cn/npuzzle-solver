package core.runner;

import core.problem.Problem;
import core.problem.ProblemType;
import core.problem.SearchTreeRecorder;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import core.solver.algorithm.heuristic.HeuristicType;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import core.util.Stopwatch;
import npuzzle.heuristic.ManhattanPredictor;
import npuzzle.model.PuzzleAction;
import npuzzle.solver.IdAStar;
import npuzzle.solver.database.DisjointPatternDatabase;
import npuzzle.model.NPuzzleProblem;
import npuzzle.model.PuzzleBoard;

import static core.solver.algorithm.heuristic.HeuristicType.*;

/**
 * 对搜索算法进行检测的主程序
 * arg0: 问题输入样例      resources/npuzzle.txt
 * arg1: 问题类型         NPUZZLE
 * arg2: 项目的哪个阶段    1
 * arg3: feeder   stud.g01.runner.PuzzleFeeder
 */

public final class SearchTester {

    // 用于保存最后一个问题和所有生成的节点
    private static Problem lastProblem = null;
    private static Deque<Node> lastSolutionPath = null;
    private static List<Node> lastGeneratedNodes = new ArrayList<>();

    public static void main(String[] args) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException, FileNotFoundException, IOException {
        // 参数验证
        if (args.length < 4) {
            System.out.println("Usage: java SearchTester <problem_file> <problem_type> <step> <feeder_class> [algorithm]");
            System.out.println("Example: java SearchTester resources/npuzzle.txt NPUZZLE 1 stud.g01.runner.PuzzleFeeder");
            System.out.println("Algorithm options: astar (default), idastar, both");
            return;
        }

        // 解析算法参数
        String algorithm = "astar"; // 默认算法
        if (args.length >= 5) {
            algorithm = args[4].toLowerCase();
        }

        // 根据args[3]提供的类名生成学生的EngineFeeder对象
        EngineFeeder feeder = (EngineFeeder)
                Class.forName(args[3])
                        .getDeclaredConstructor().newInstance();

        // 从文件读入所有输入样例的文本
        Scanner scanner = new Scanner(new File(args[0]));
        ArrayList<String> problemLines = getProblemLines(scanner);
        scanner.close();

        // feeder从输入样例文本获取问题的所有实例
        ArrayList<Problem> problems = feeder.getProblems(problemLines);

        // 当前问题的类型 args[1]
        ProblemType type = ProblemType.valueOf(args[1]);
        // 任务第几阶段 args[2]
        int step = Integer.parseInt(args[2]);

        System.out.println("=== 开始测试 ===");
        System.out.println("问题类型: " + type);
        System.out.println("阶段: " + step);
        System.out.println("问题数量: " + problems.size());
        //System.out.println("算法: " + algorithm);
        System.out.println();

        // === 第三阶段NPUZZLE ===
        if (type == ProblemType.NPUZZLE && step == 3) {
            runStage3NPuzzleWithSmartSelection(problems, feeder, algorithm);
        } else {
            // 其他阶段保持原有逻辑
            ArrayList<HeuristicType> heuristics = getHeuristicTypes(type, step);

            for (HeuristicType heuristicType : heuristics) {
                System.out.println("=== 使用启发式: " + heuristicType + " ===");

                if (algorithm.equals("astar") || algorithm.equals("both")) {
                    System.out.println("--- A* 算法 ---");
                    solveProblems(problems, feeder.getAStar(heuristicType), heuristicType);
                }

                if (algorithm.equals("idastar") || algorithm.equals("both")) {
                    System.out.println("--- IdAStar 算法 ---");
                    solveProblemsWithIdaStar(problems, feeder, heuristicType);
                }
                System.out.println();
            }
        }
    }



    /**
     * 根据问题类型和当前阶段，获取所有启发函数的类型
     * @param type
     * @param step
     * @return
     */
    private static ArrayList<HeuristicType> getHeuristicTypes(ProblemType type, int step) {
        //求解当前问题在当前阶段可用的启发函数类型列表
        ArrayList<HeuristicType> heuristics = new ArrayList<>();
        //根据不同的问题类型，进行不同的测试
        if (type == ProblemType.PATHFINDING) {
            heuristics.add(PF_GRID);
            heuristics.add(PF_EUCLID);
        }
        else if (type == ProblemType.NPUZZLE) {
            //NPuzzle问题的第一阶段，使用不在位将牌和曼哈顿距离
            if (step == 1) {
                heuristics.add(MISPLACED);
                heuristics.add(MANHATTAN);
            }
            //NPuzzle问题的第二阶段
            else if (step == 2) {
                heuristics.add(MANHATTAN);
                heuristics.add(LINEAR_CONFLICT);
            }
            //NPuzzle问题的第三阶段
            else if (step == 3) {
                heuristics.add(DISJOINT_PATTERN);
                //heuristics.add(MANHATTAN); // 备用启发式
            }
        }
        // 可以添加其他问题类型的处理
        else {
            System.out.println("未知的问题类型: " + type);
        }
        return heuristics;
    }

    /**
     * 使用给定的searcher，求解问题集合中的所有问题，同时使用解检测器对求得的解进行检测
     * @param problems     问题集合
     * @param searcher     searcher
     * @param heuristicType 使用哪种启发函数？
     */
    private static void solveProblems(ArrayList<Problem> problems, AbstractSearcher searcher, HeuristicType heuristicType) {
        int problemIndex = 1;
        for (Problem problem : problems) {
            System.out.println("--- 问题 " + problemIndex + " ---");

            // 检查问题是否有解
            if (!problem.solvable()) {
                System.out.println("此问题无解，跳过");
                problemIndex++;
                continue;
            }

            // 对于模式数据库，检查是否为4x4问题
            if (heuristicType == HeuristicType.DISJOINT_PATTERN) {
                if (problem instanceof NPuzzleProblem) {
                    NPuzzleProblem npuzzle = (NPuzzleProblem) problem;
                    PuzzleBoard board = (PuzzleBoard) npuzzle.root().getState();
                    if (board.getSize() != 4) {
                        System.out.println("模式数据库仅支持4x4问题，跳过");
                        problemIndex++;
                        continue;
                    }
                }
            }

            // 显示初始状态和目标状态
            System.out.println("初始状态:");
            problem.root().getState().draw();
            System.out.println("目标状态:");
            ((NPuzzleProblem)problem).getGoal().draw();

            // 使用搜索算法求解问题
            SearchTreeRecorder.start();
            Stopwatch timer1 = new Stopwatch();
            Deque<Node> path = searcher.search(problem);
            List<Node> recordedNodes = SearchTreeRecorder.stopAndGetSnapshot();
            double time1 = timer1.elapsedTime();

            if (path == null) {
                System.out.println("未找到解" + "，执行时间: " + time1 + "s，" +
                        "生成节点: " + searcher.nodesGenerated() + "，" +
                        "扩展节点: " + searcher.nodesExpanded());
            } else {
                // 解路径的可视化
                problem.showSolution(path);

                System.out.println("启发函数: " + heuristicType +
                        "，解路径长度: " + (path.size() - 1) +
                        "，执行时间: " + time1 + "s，" +
                        "生成节点: " + searcher.nodesGenerated() +
                        "，扩展节点: " + searcher.nodesExpanded());

                // 保存最后一个问题和解决方案
                lastProblem = problem;
                lastSolutionPath = path;
                lastGeneratedNodes = recordedNodes;
            }
            System.out.println();
            problemIndex++;
        }
    }

    /**
     * 使用IdAStar求解问题
     */
    private static void solveProblemsWithIdaStar(ArrayList<Problem> problems, EngineFeeder feeder, HeuristicType heuristicType) {
        int problemIndex = 1;
        AbstractSearcher searcher = feeder.getIdaStar(heuristicType);

        for (Problem problem : problems) {
            System.out.println("--- 问题 " + problemIndex + " (IdAStar) ---");

            // 检查问题是否有解
            if (!problem.solvable()) {
                System.out.println("此问题无解，跳过");
                problemIndex++;
                continue;
            }

            // 显示初始状态和目标状态
            System.out.println("初始状态:");
            problem.root().getState().draw();
            System.out.println("目标状态:");
            if (problem instanceof NPuzzleProblem) {
                ((NPuzzleProblem) problem).getGoal().draw();
            }

            // 使用IdAStar搜索算法求解问题
            SearchTreeRecorder.start();
            Stopwatch timer = new Stopwatch();
            Deque<core.solver.queue.Node> path = searcher.search(problem);
            List<Node> recordedNodes = SearchTreeRecorder.stopAndGetSnapshot();
            double time = timer.elapsedTime();

            if (path == null) {
                System.out.println("未找到解" + "，执行时间: " + time + "s，" +
                        "生成节点: " + searcher.nodesGenerated() + "，" +
                        "扩展节点: " + searcher.nodesExpanded());
            } else {
                // 解路径的可视化
                problem.showSolution(path);

                System.out.println("启发函数: " + heuristicType +
                        "，解路径长度: " + (path.size() - 1) +
                        "，执行时间: " + time + "s，" +
                        "生成节点: " + searcher.nodesGenerated() +
                        "，扩展节点: " + searcher.nodesExpanded());

                // 保存最后一个问题和解决方案
                lastProblem = problem;
                lastSolutionPath = path;
                lastGeneratedNodes = recordedNodes;
            }
            System.out.println();
            problemIndex++;
        }
    }

    /**
     * 从文件读入问题实例的字符串，放入字符串数组里
     * @param scanner
     * @return
     */
    public static ArrayList<String> getProblemLines(Scanner scanner) {
        ArrayList<String> lines = new ArrayList<>();
        while (scanner.hasNext()){
            String line = scanner.nextLine().trim();
            // 跳过空行和注释行
            if (!line.isEmpty() && !line.startsWith("#")) {
                lines.add(line);
            }
        }
        return lines;
    }


    /**
     * 第三阶段NPUZZLE根据问题尺寸选择最佳算法
     */
    private static void runStage3NPuzzleWithSmartSelection(ArrayList<Problem> problems, EngineFeeder feeder, String algorithm) {
        // 获取第三阶段的启发式类型
        ArrayList<HeuristicType> heuristics = getHeuristicTypes(ProblemType.NPUZZLE, 3);

        for (HeuristicType heuristicType : heuristics) {
            System.out.println("=== 使用启发式: " + heuristicType + " ===");

            // 对于模式数据库，需要特殊处理
            if (heuristicType == HeuristicType.DISJOINT_PATTERN) {
                System.out.println("--- 模式数据库算法（仅适用于4x4问题） ---");
                solveProblemsWithSmartPatternDB(problems, feeder, algorithm);
            } else {
                // 其他启发式保持原有逻辑
                if (algorithm.equals("astar") || algorithm.equals("both")) {
                    System.out.println("--- A* 算法 ---");
                    solveProblems(problems, feeder.getAStar(heuristicType), heuristicType);
                }

                if (algorithm.equals("idastar") || algorithm.equals("both")) {
                    System.out.println("--- IdAStar 算法 ---");
                    solveProblemsWithIdaStar(problems, feeder, heuristicType);
                }
            }
            System.out.println();
        }

        // 在所有问题处理完后，保存最后一个问题的信息
        if (lastProblem != null && lastSolutionPath != null) {
            try {
                saveLastProblemAndSolution();
            } catch (IOException e) {
                System.err.println("保存文件时出错: " + e.getMessage());
            }
        }
    }

    /**
     * 智能模式数据库求解 - 根据问题尺寸自动选择算法
     */
    private static void solveProblemsWithSmartPatternDB(ArrayList<Problem> problems, EngineFeeder feeder, String algorithm) {

        int problemIndex = 1;

        // 预先创建两种搜索器
        AbstractSearcher patternDBSearcher = createPatternDBIdaStar(feeder);
        AbstractSearcher manhattanSearcher = feeder.getIdaStar(HeuristicType.MANHATTAN);
        AbstractSearcher aStarSearcher = feeder.getAStar(HeuristicType.MANHATTAN);

        for (Problem problem : problems) {
            System.out.println("--- 问题 " + problemIndex + " ---");

            // 检查问题是否有解
            if (!problem.solvable()) {
                System.out.println("此问题无解，跳过");
                problemIndex++;
                continue;
            }

            // 智能选择算法：4x4问题使用模式数据库，3x3问题使用曼哈顿距离
            AbstractSearcher searcher;
            String algorithmUsed;
            HeuristicType heuristicUsed;

            if (problem instanceof NPuzzleProblem) {
                NPuzzleProblem npuzzle = (NPuzzleProblem) problem;
                PuzzleBoard board = (PuzzleBoard) npuzzle.root().getState();

                if (board.getSize() == 4) {
                    System.out.println("算法:IDA* + 模式数据库");
                    // 4x4问题：使用模式数据库
                    /*
                    if (algorithm.equals("astar")) {
                        searcher = feeder.getAStar(HeuristicType.DISJOINT_PATTERN);
                        algorithmUsed = "A*";
                    } else {
                        searcher = patternDBSearcher;
                        algorithmUsed = "IDA*";
                    }
                    heuristicUsed = HeuristicType.DISJOINT_PATTERN;
                    System.out.println("检测到4x4问题，使用模式数据库");
                    */
                    // 直接指定使用 IDA*，忽略命令行的 astar 配置
                    searcher = patternDBSearcher;
                    algorithmUsed = "IDA*";
                    heuristicUsed = HeuristicType.DISJOINT_PATTERN;
                } else {
                    // 3x3或其他尺寸问题：使用曼哈顿距离
                    if (algorithm.equals("astar")) {
                        searcher = aStarSearcher;
                        algorithmUsed = "A*";
                    } else {
                        searcher = manhattanSearcher;
                        algorithmUsed = "IDA*";
                    }
                    heuristicUsed = HeuristicType.MANHATTAN;
                    System.out.println("检测到" + board.getSize() + "x" + board.getSize() + "问题，使用曼哈顿距离");
                }
            } else {
                // 非NPuzzle问题，使用默认算法
                if (algorithm.equals("astar")) {
                    searcher = aStarSearcher;
                    algorithmUsed = "A*";
                } else {
                    searcher = manhattanSearcher;
                    algorithmUsed = "IDA*";
                }
                heuristicUsed = HeuristicType.MANHATTAN;
            }

            // 显示初始状态和目标状态
            System.out.println("初始状态:");
            problem.root().getState().draw();
            System.out.println("目标状态:");
            if (problem.getGoal() != null) {
                problem.getGoal().draw();
            }

            // 使用选择的搜索算法求解问题
            SearchTreeRecorder.start();
            Stopwatch timer = new Stopwatch();
            Deque<core.solver.queue.Node> path = searcher.search(problem);
            List<Node> recordedNodes = SearchTreeRecorder.stopAndGetSnapshot();
            double time = timer.elapsedTime();

            if (path == null) {
                System.out.println("未找到解" + "，执行时间: " + time + "s，" +
                        "生成节点: " + searcher.nodesGenerated() + "，" +
                        "扩展节点: " + searcher.nodesExpanded());
            } else {
                // 解路径的可视化（如果路径不太长）
                if (path.size() <= 100) {
                    problem.showSolution(path);
                } else {
                    System.out.println("解路径过长(" + path.size() + "步)，跳过显示");
                }

                System.out.println("算法: " + algorithmUsed +
                        "，启发函数: " + heuristicUsed +
                        "，解路径长度: " + (path.size() - 1) +
                        "，执行时间: " + time + "s，" +
                        "生成节点: " + searcher.nodesGenerated() +
                        "，扩展节点: " + searcher.nodesExpanded());

                // 第三阶段时限检查
                if (time <= 60.0) {
                    System.out.println("? 满足第三阶段时限要求（60秒）");
                } else {
                    System.out.println("? 不满足第三阶段时限要求（60秒）");
                }

                // 保存最后一个问题和解决方案
                lastProblem = problem;
                lastSolutionPath = path;
                lastGeneratedNodes = recordedNodes;
            }
            System.out.println();
            problemIndex++;
        }

        // 在所有问题处理完后，保存最后一个问题的信息
        if (lastProblem != null && lastSolutionPath != null) {
            try {
                saveLastProblemAndSolution();
            } catch (IOException e) {
                System.err.println("保存文件时出错: " + e.getMessage());
            }
        }
    }

    private static String stateToString(PuzzleBoard board) {
        int[] tiles = board.getPuzzleBoard();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tiles.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(tiles[i]);
        }
        return sb.toString();
    }

    /**
     * 将动作转换为方向字符串
     */
    private static String actionToDirectionString(core.problem.Action action) {
        if (action instanceof PuzzleAction) {
            PuzzleAction puzzleAction = (PuzzleAction) action;
            return puzzleAction.getDirection().name();
        }
        return "";
    }

    /**
     * 保存最后一个问题和解决方案到文件
     */
    private static void saveLastProblemAndSolution() throws IOException {
        if (!(lastProblem instanceof NPuzzleProblem)) {
            return;
        }

        NPuzzleProblem npuzzle = (NPuzzleProblem) lastProblem;
        PuzzleBoard initialState = (PuzzleBoard) npuzzle.root().getState();
        PuzzleBoard goalState = (PuzzleBoard) npuzzle.getGoal();

        // 确保bin目录存在
        File binDir = new File("bin");
        if (!binDir.exists()) {
            binDir.mkdirs();
        }

        // 1. 保存问题到bin/problem.txt
        saveProblemToFile(npuzzle, initialState, goalState);

        // 2. 保存搜索树到bin/solution.txt
        saveSearchTreeToFile();

        // 3. 保存动作序列到bin/solutionAnimation.txt
        saveActionSequenceToFile();
    }

    /**
     * 保存问题到problem.txt
     */
    private static void saveProblemToFile(NPuzzleProblem problem, PuzzleBoard initialState, PuzzleBoard goalState) throws IOException {
        File problemFile = new File("bin/problem.txt");
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(problemFile), "GBK"))) {
            int size = initialState.getSize();
            String startState = stateToString(initialState);
            String goalStateStr = stateToString(goalState);
            writer.println(size + " " + startState + " " + goalStateStr);
        }
    }

    /**
     * 保存搜索树到solution.txt
     */
    private static void saveSearchTreeToFile() throws IOException {
        if (lastGeneratedNodes == null || lastGeneratedNodes.isEmpty()) {
            return;
        }

        if (lastGeneratedNodes.size() > 50000) {
            System.err.println("警告: 生成节点数 (" + lastGeneratedNodes.size() + ") 过大，为防止内存溢出，跳过 solution.txt 的生成。");
            return;
        }

        File solutionFile = new File("bin/solution.txt");
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(solutionFile), "GBK"))) {

            Map<Node, List<Node>> childrenMap = new HashMap<>();
            Node rootNode = null;
            for (Node node : lastGeneratedNodes) {
                if (node == null) continue;
                if (node.getParent() == null) {
                    if (rootNode == null) {
                        rootNode = node;
                    }
                } else {
                    childrenMap.computeIfAbsent(node.getParent(), k -> new ArrayList<>()).add(node);
                }
            }

            if (rootNode == null) {
                rootNode = lastGeneratedNodes.get(0);
            }

            Map<Node, Integer> nodeIds = new HashMap<>();
            List<Node> orderedNodes = new ArrayList<>();
            Deque<Node> queue = new ArrayDeque<>();

            if (rootNode != null) {
                nodeIds.put(rootNode, 0);
                orderedNodes.add(rootNode);
                queue.add(rootNode);
            }

            while (!queue.isEmpty()) {
                Node current = queue.removeFirst();
                List<Node> children = childrenMap.getOrDefault(current, Collections.emptyList());
                for (Node child : children) {
                    if (!nodeIds.containsKey(child)) {
                        nodeIds.put(child, nodeIds.size());
                        orderedNodes.add(child);
                        queue.add(child);
                    }
                }
            }

            // 处理可能未连接到根的节点（理论上不会发生）
            for (Node node : lastGeneratedNodes) {
                if (!nodeIds.containsKey(node)) {
                    nodeIds.put(node, nodeIds.size());
                    orderedNodes.add(node);
                }
            }

            for (Node node : orderedNodes) {
                PuzzleBoard board = (PuzzleBoard) node.getState();
                int size = board.getSize();
                int parentId;
                if (node.getParent() == null) {
                    parentId = -1;
                } else {
                    parentId = nodeIds.getOrDefault(node.getParent(), 0);
                }

                int g = node.getPathCost();
                int h = node.getHeuristic();
                int f = g + h;
                String stateStr = stateToString(board);

                writer.println(size + " " + parentId + " " + g + " " + h + " " + f + " " + stateStr);
            }
        }
    }

    /**
     * 保存动作序列到solutionAnimation.txt
     */
    private static void saveActionSequenceToFile() throws IOException {
        File actionFile = new File("bin/solutionAnimation.txt");
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(actionFile), "GBK"))) {

            // 路径是从根节点的子节点到目标节点（不包含根节点）
            // 每个节点都有动作（从父节点到当前节点的动作）
            List<String> actions = new ArrayList<>();
            for (Node node : lastSolutionPath) {
                if (node.getAction() != null) {
                    String direction = actionToDirectionString(node.getAction());
                    if (!direction.isEmpty()) {
                        actions.add(direction);
                    }
                }
            }

            // 输出动作序列，空格分隔
            if (!actions.isEmpty()) {
                writer.println(String.join(" ", actions));
            }
        }
    }

    /**
     * 创建使用模式数据库的IDA*
     */
    private static AbstractSearcher createPatternDBIdaStar(EngineFeeder feeder) {
        try {
            // 加载模式数据库
            DisjointPatternDatabase patternDB =
                    DisjointPatternDatabase.loadCompactDatabases();

            // 创建Frontier
            core.solver.queue.Frontier frontier = feeder.getFrontier(core.solver.queue.EvaluationType.FULL);

            // 创建IDA* - 使用您现有的IdAStar类
            return new IdAStar(frontier, patternDB);

        } catch (Exception e) {
            System.err.println("模式数据库加载失败: " + e.getMessage());
            System.out.println("使用曼哈顿距离回退");

            // 回退到曼哈顿距离
            core.solver.algorithm.heuristic.Predictor manhattan =
                    new ManhattanPredictor();
            core.solver.queue.Frontier frontier = feeder.getFrontier(core.solver.queue.EvaluationType.FULL);
            return new IdAStar(frontier, manhattan);
        }
    }

    /**
     * 在程序结束时保存最后一个问题的信息
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (lastProblem != null && lastSolutionPath != null) {
                    saveLastProblemAndSolution();
                }
            } catch (IOException e) {
                System.err.println("保存文件时出错: " + e.getMessage());
            }
        }));
    }
}