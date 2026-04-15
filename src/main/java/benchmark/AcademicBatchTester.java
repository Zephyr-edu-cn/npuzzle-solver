package benchmark;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.queue.Node;
import npuzzle.heuristic.LinearConflictPredictor;
import npuzzle.heuristic.ManhattanPredictor;
import npuzzle.solver.IdAStar;
import npuzzle.solver.database.DisjointPatternDatabase;
import core.solver.algorithm.searcher.BestFirstSearcher;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Academic-Grade Batch Benchmark Tool.
 * Methodological safeguards:
 * 1. Global JIT Warmup to eliminate cold-start bias.
 * 2. Intersection-based time statistics to prevent Survivor Bias.
 * 3. Multi-trial execution with Variance/Standard Deviation analysis.
 * 4. Comprehensive baselines (including A* + Manhattan).
 */
public class AcademicBatchTester {

    private static final String INPUT_FILE = "datasets/my_benchmark_100.txt";
    private static final String OUTPUT_CSV = "academic_results.csv";
    private static final int TIMEOUT_SECONDS = 60;
    private static final int WARMUP_INSTANCES = 5;
    private static final int TRIALS = 3;

    // Added A*_Manhattan as the ultimate exploding baseline
    private static final String[] CONFIGS = {
            "A*_Manhattan", "IDA*_Manhattan", "IDA*_LinearConflict", "IDA*_PDB_OOP", "IDA*_PDB_Bitboard"
    };

    private static final Map<Integer, Map<String, Map<Integer, TestResult>>> allTrialsData = new HashMap<>();

    static class TestResult {
        boolean solved;
        long timeMs;
        long expandedNodes;
        double ebf;

        TestResult(boolean solved, long timeMs, long expandedNodes, double ebf) {
            this.solved = solved;
            this.timeMs = timeMs;
            this.expandedNodes = expandedNodes;
            this.ebf = ebf;
        }
    }

    public static void main(String[] args) throws Exception {
        EngineFeeder feeder = (EngineFeeder) Class.forName("npuzzle.runner.PuzzleFeeder").getDeclaredConstructor().newInstance();
        ArrayList<Problem> allProblems = getProblems(feeder, INPUT_FILE);

        DisjointPatternDatabase pdb = DisjointPatternDatabase.loadCompactDatabases();
        ManhattanPredictor manhattan = new ManhattanPredictor();
        LinearConflictPredictor linearConflict = new LinearConflictPredictor();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        // Phase 1: JIT Warmup
        System.out.println("\n[PHASE 1] Global JVM JIT Warmup (All configs)...");
        int warmupCount = Math.min(WARMUP_INSTANCES, allProblems.size());
        for (int i = 0; i < warmupCount; i++) {
            Problem p = allProblems.get(i);

            // Warmup A*
            BestFirstSearcher astar = new BestFirstSearcher(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), manhattan);
            runConfigSilent(astar, p, executor, 2);

            // Warmup IDA* variants
            runConfigSilent(new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), manhattan), p, executor, 2);
            runConfigSilent(new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), linearConflict), p, executor, 2);
            IdAStar pOop = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb); pOop.setUseGeneralPath(true);
            runConfigSilent(pOop, p, executor, 2);
            IdAStar pBit = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb); pBit.setUseGeneralPath(false);
            runConfigSilent(pBit, p, executor, 2);
        }

        // Phase 2: Evaluation
        PrintWriter csvWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_CSV), "UTF-8"));
        csvWriter.println("Trial,Instance_ID,Configuration,Status,Solution_Length,Generated_Nodes,Expanded_Nodes,Time_ms,EBF");

        System.out.println("\n[PHASE 2] Formal Multi-Trial Evaluation Started...");
        for (int trial = 1; trial <= TRIALS; trial++) {
            allTrialsData.put(trial, new LinkedHashMap<>());
            for (String config : CONFIGS) allTrialsData.get(trial).put(config, new HashMap<>());

            for (int i = 0; i < allProblems.size(); i++) {
                Problem p = allProblems.get(i);
                int instanceId = i + 1;
                System.out.println("\n[Trial " + trial + "] Processing Instance [" + instanceId + "/" + allProblems.size() + "]");

                // 0. A* + Manhattan
                BestFirstSearcher astarSearcher = new BestFirstSearcher(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), manhattan);
                runConfig(trial, instanceId, CONFIGS[0], astarSearcher, p, executor, csvWriter);

                // 1. IDA* + Manhattan
                IdAStar mSearcher = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), manhattan);
                runConfig(trial, instanceId, CONFIGS[1], mSearcher, p, executor, csvWriter);

                // 2. IDA* + Linear Conflict
                IdAStar lSearcher = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), linearConflict);
                runConfig(trial, instanceId, CONFIGS[2], lSearcher, p, executor, csvWriter);

                // 3. IDA* + PDB (OOP)
                IdAStar pOopSearcher = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb);
                pOopSearcher.setUseGeneralPath(true);
                runConfig(trial, instanceId, CONFIGS[3], pOopSearcher, p, executor, csvWriter);

                // 4. IDA* + PDB (Bitboard)
                IdAStar pBitSearcher = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb);
                pBitSearcher.setUseGeneralPath(false);
                runConfig(trial, instanceId, CONFIGS[4], pBitSearcher, p, executor, csvWriter);
            }
        }
        executor.shutdownNow();
        csvWriter.close();

        // Phase 3: Statistical Summary
        printUnbiasedStatisticalSummary(allProblems.size());
    }

    // Changed parameter from IdAStar to AbstractSearcher
    private static void runConfigSilent(core.solver.algorithm.searcher.AbstractSearcher searcher, Problem problem, ExecutorService executor, int timeout) {
        Future<Deque<Node>> future = executor.submit(() -> searcher.search(problem));
        try { future.get(timeout, TimeUnit.SECONDS); } catch (Exception ignore) { future.cancel(true); }
    }

    // Changed parameter from IdAStar to AbstractSearcher
    private static void runConfig(int trial, int instanceId, String configName, core.solver.algorithm.searcher.AbstractSearcher searcher, Problem problem, ExecutorService executor, PrintWriter csvWriter) {
        System.out.printf("   ├─ %-22s -> ", configName);

        Future<Deque<Node>> future = executor.submit(() -> searcher.search(problem));
        long startTime = System.currentTimeMillis();
        try {
            Deque<Node> path = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long timeMs = System.currentTimeMillis() - startTime;

            if (path != null) {
                int depth = path.size() - 1;
                long generated = searcher.nodesGenerated();
                long expanded = searcher.nodesExpanded();
                double ebf = calculateEBF(expanded, depth);
                System.out.printf("[SUCCESS] Depth:%d | Time:%5d ms | Nodes Exp:%,10d | EBF:%.3f\n", depth, timeMs, expanded, ebf);
                csvWriter.printf("%d,%d,%s,Solved,%d,%d,%d,%d,%.3f\n", trial, instanceId, configName, depth, generated, expanded, timeMs, ebf);
                allTrialsData.get(trial).get(configName).put(instanceId, new TestResult(true, timeMs, expanded, ebf));
            } else {
                System.out.println("[FAIL] (No Solution)");
                csvWriter.printf("%d,%d,%s,NoSolution,-1,-1,-1,-1,-1\n", trial, instanceId, configName);
                allTrialsData.get(trial).get(configName).put(instanceId, new TestResult(false, 0, 0, 0));
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            System.out.printf("[TIMEOUT/OOM] (>%d sec) | Nodes Exp: %,d\n", TIMEOUT_SECONDS, searcher.nodesExpanded());
            csvWriter.printf("%d,%d,%s,Timeout,-1,%d,%d,>%d,-1\n", trial, instanceId, configName, searcher.nodesGenerated(), searcher.nodesExpanded(), TIMEOUT_SECONDS * 1000);
            allTrialsData.get(trial).get(configName).put(instanceId, new TestResult(false, TIMEOUT_SECONDS * 1000L, searcher.nodesExpanded(), 0));
        } catch (Exception e) {
            // Treat OutOfMemoryError and other exceptions as FAIL
            future.cancel(true);
            System.out.printf("[ERROR/OOM] Nodes Exp: %,d\n", searcher.nodesExpanded());
            csvWriter.printf("%d,%d,%s,Error,-1,%d,%d,-1,-1\n", trial, instanceId, configName, searcher.nodesGenerated(), searcher.nodesExpanded());
            allTrialsData.get(trial).get(configName).put(instanceId, new TestResult(false, 0, 0, 0));
        }
        csvWriter.flush();
    }

    private static void printUnbiasedStatisticalSummary(int totalInstances) {
        System.out.println("\n=========================================================================================");
        System.out.println("📊 Unbiased Statistical Summary (Averaged over Trial 2 & Trial 3)");
        System.out.println("Note: Time statistics are computed ONLY on instances solved by ALL configurations to prevent Survivor Bias.");
        System.out.println("=========================================================================================");
        System.out.printf("%-22s | %-12s | %-15s | %-12s | %-15s | %-8s\n",
                "Configuration", "Success Rate", "Mean Time(ms)", "StdDev(ms)", "Mean Expanded", "Mean EBF");
        System.out.println("-".repeat(95));

        Set<Integer> universallySolved = new HashSet<>();
        for (int i = 1; i <= totalInstances; i++) universallySolved.add(i);

        for (int trial = 2; trial <= TRIALS; trial++) {
            for (String config : CONFIGS) {
                for (int i = 1; i <= totalInstances; i++) {
                    if (!allTrialsData.get(trial).get(config).get(i).solved) universallySolved.remove(i);
                }
            }
        }

        for (String config : CONFIGS) {
            int instanceSolvedCount = 0;
            for (int i = 1; i <= totalInstances; i++) {
                boolean solvedInAllValidTrials = true;
                for (int trial = 2; trial <= TRIALS; trial++) {
                    if (!allTrialsData.get(trial).get(config).get(i).solved) solvedInAllValidTrials = false;
                }
                if (solvedInAllValidTrials) instanceSolvedCount++;
            }

            List<Long> validTimes = new ArrayList<>();
            long totalNodes = 0;
            double totalEbf = 0;
            int nodeSamples = 0;

            for (int trial = 2; trial <= TRIALS; trial++) {
                for (int i = 1; i <= totalInstances; i++) {
                    TestResult res = allTrialsData.get(trial).get(config).get(i);
                    if (res.solved) {
                        totalNodes += res.expandedNodes;
                        totalEbf += res.ebf;
                        nodeSamples++;
                    }
                    if (universallySolved.contains(i)) validTimes.add(res.timeMs);
                }
            }

            double successRate = (instanceSolvedCount * 100.0) / totalInstances;

            double meanTime = 0, stdDevTime = 0;
            if (!validTimes.isEmpty()) {
                long sum = 0; for (long t : validTimes) sum += t;
                meanTime = (double) sum / validTimes.size();
                double variance = 0;
                for (long t : validTimes) variance += Math.pow(t - meanTime, 2);
                stdDevTime = Math.sqrt(variance / validTimes.size());
            }

            String timeStr = validTimes.isEmpty() ? "N/A" : String.format("%.1f", meanTime);
            String stdStr = validTimes.isEmpty() ? "N/A" : String.format("±%.1f", stdDevTime);
            String nodeStr = nodeSamples > 0 ? String.format("%,d", totalNodes / nodeSamples) : "N/A";
            String ebfStr = nodeSamples > 0 ? String.format("%.3f", totalEbf / nodeSamples) : "N/A";

            System.out.printf("%-22s | %6.2f%%      | %-15s | %-12s | %-15s | %-8s\n",
                    config, successRate, timeStr, stdStr, nodeStr, ebfStr);
        }
        System.out.println("=========================================================================================\n");
    }

    private static double calculateEBF(long expandedNodes, int depth) {
        if (depth == 0 || expandedNodes <= depth + 1) return 1.0;
        double low = 1.0, high = 100.0, epsilon = 1e-5;
        while (high - low > epsilon) {
            double mid = (low + high) / 2.0;
            double calcNodes = (Math.pow(mid, depth + 1) - 1) / (mid - 1);
            if (calcNodes < expandedNodes) low = mid; else high = mid;
        }
        return (low + high) / 2.0;
    }

    private static ArrayList<Problem> getProblems(EngineFeeder feeder, String inputFile) throws Exception {
        Scanner scanner = new Scanner(new File(inputFile));
        ArrayList<String> lines = new ArrayList<>();
        while (scanner.hasNext()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
        }
        scanner.close();
        return feeder.getProblems(lines);
    }
}