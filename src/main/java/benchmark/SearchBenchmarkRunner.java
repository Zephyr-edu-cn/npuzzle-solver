package benchmark;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import npuzzle.heuristic.LinearConflictPredictor;
import npuzzle.heuristic.ManhattanPredictor;
import npuzzle.solver.IdAStar;
import npuzzle.solver.database.DisjointPatternDatabase;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Batch benchmark for the solver configurations used in the ablation study.
 * Search time is measured inside the worker task with System.nanoTime().
 * Configuration order is counterbalanced across trials and instances.
 */
public class SearchBenchmarkRunner {
    private static final String INPUT_FILE = System.getProperty(
            "npuzzle.benchmark.input", "datasets/my_benchmark_100.txt");
    private static final String OUTPUT_CSV = System.getProperty(
            "npuzzle.benchmark.output", "benchmark_results/Search_results_v2.csv");
    private static final int TIMEOUT_SECONDS = Integer.getInteger("npuzzle.benchmark.timeoutSeconds", 120);
    private static final int WARMUP_INSTANCES = Integer.getInteger("npuzzle.benchmark.warmupInstances", 5);
    private static final int TRIALS = Integer.getInteger("npuzzle.benchmark.trials", 5);
    private static final int MAX_INSTANCES = Integer.getInteger(
            "npuzzle.benchmark.maxInstances", Integer.MAX_VALUE);

    static final String[] CONFIGS = {
            "IDA*_Manhattan", "IDA*_LinearConflict", "IDA*_PDB_OOP",
            "IDA*_PDB_MutableArray", "IDA*_PDB_Bitboard"
    };

    private static final Map<Integer, Map<String, Map<Integer, TestResult>>> allTrialsData =
            new LinkedHashMap<>();
    static class TestResult {
        final boolean solved;
        final int solutionLength;
        final long timeNs;
        final long generatedNodes;
        final long expandedNodes;
        final double ebf;

        TestResult(boolean solved, int solutionLength, long timeNs,
                   long generatedNodes, long expandedNodes, double ebf) {
            this.solved = solved;
            this.solutionLength = solutionLength;
            this.timeNs = timeNs;
            this.generatedNodes = generatedNodes;
            this.expandedNodes = expandedNodes;
            this.ebf = ebf;
        }
    }

    private static class TimedSearchResult {
        final Deque<Node> path;
        final long timeNs;
        final boolean outOfMemory;

        TimedSearchResult(Deque<Node> path, long timeNs, boolean outOfMemory) {
            this.path = path;
            this.timeNs = timeNs;
            this.outOfMemory = outOfMemory;
        }
    }

    public static void main(String[] args) throws Exception {
        if (TRIALS < 1 || MAX_INSTANCES < 1 || TIMEOUT_SECONDS < 1) {
            throw new IllegalArgumentException("Trials, max instances, and timeout must be positive.");
        }

        EngineFeeder feeder = (EngineFeeder) Class.forName("npuzzle.runner.PuzzleFeeder")
                .getDeclaredConstructor().newInstance();
        ArrayList<Problem> allProblems = getProblems(feeder, INPUT_FILE);
        if (allProblems.size() > MAX_INSTANCES) {
            allProblems = new ArrayList<>(allProblems.subList(0, MAX_INSTANCES));
        }
        if (allProblems.isEmpty()) {
            throw new IllegalArgumentException("No benchmark instances were loaded from " + INPUT_FILE);
        }

        DisjointPatternDatabase pdb = DisjointPatternDatabase.loadCompactDatabases();
        ManhattanPredictor manhattan = new ManhattanPredictor();
        LinearConflictPredictor linearConflict = new LinearConflictPredictor();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        allTrialsData.clear();

        try {
            runWarmup(feeder, allProblems, pdb, manhattan, linearConflict, executor);
            runEvaluation(feeder, allProblems, pdb, manhattan, linearConflict, executor);
        } finally {
            executor.shutdownNow();
        }
        printStatisticalSummary(allProblems.size());
    }
    private static void runWarmup(EngineFeeder feeder, List<Problem> problems,
                                  DisjointPatternDatabase pdb, ManhattanPredictor manhattan,
                                  LinearConflictPredictor linearConflict, ExecutorService executor) {
        System.out.println("\n[PHASE 1] JVM warmup (all configurations)...");
        int warmupCount = Math.min(WARMUP_INSTANCES, problems.size());
        for (int i = 0; i < warmupCount; i++) {
            for (String config : CONFIGS) {
                AbstractSearcher searcher = createSearcher(config, feeder, pdb, manhattan, linearConflict);
                runConfigSilent(searcher, problems.get(i), executor, Math.min(2, TIMEOUT_SECONDS));
            }
        }
    }

    private static void runEvaluation(EngineFeeder feeder, List<Problem> problems,
                                      DisjointPatternDatabase pdb, ManhattanPredictor manhattan,
                                      LinearConflictPredictor linearConflict,
                                      ExecutorService executor) throws IOException {
        File outputFile = new File(OUTPUT_CSV);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create benchmark output directory: " + parent);
        }

        try (PrintWriter csvWriter = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            csvWriter.println("Trial,Instance_ID,Execution_Order,Configuration,Status,Solution_Length," +
                    "Generated_Nodes,Expanded_Nodes,Search_Time_ns,Search_Time_ms,EBF");

            System.out.println("\n[PHASE 2] Counterbalanced multi-trial evaluation...");
            for (int trial = 1; trial <= TRIALS; trial++) {
                allTrialsData.put(trial, new LinkedHashMap<>());
                for (String config : CONFIGS) {
                    allTrialsData.get(trial).put(config, new HashMap<>());
                }

                for (int i = 0; i < problems.size(); i++) {
                    int instanceId = i + 1;
                    String[] order = configOrder(trial, instanceId);
                    System.out.printf("\n[Trial %d] Instance [%d/%d]\n", trial, instanceId, problems.size());
                    for (int position = 0; position < order.length; position++) {
                        String config = order[position];
                        AbstractSearcher searcher = createSearcher(
                                config, feeder, pdb, manhattan, linearConflict);
                        runConfig(trial, instanceId, position + 1, config, searcher,
                                problems.get(i), executor, csvWriter);
                    }
                }
            }
        }
    }
    private static AbstractSearcher createSearcher(String config, EngineFeeder feeder,
                                                    DisjointPatternDatabase pdb,
                                                    ManhattanPredictor manhattan,
                                                    LinearConflictPredictor linearConflict) {
        IdAStar searcher;
        switch (config) {
            case "IDA*_Manhattan":
                return new IdAStar(feeder.getFrontier(EvaluationType.FULL), manhattan);
            case "IDA*_LinearConflict":
                return new IdAStar(feeder.getFrontier(EvaluationType.FULL), linearConflict);
            case "IDA*_PDB_OOP":
                searcher = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
                searcher.setUseGeneralPath(true);
                return searcher;
            case "IDA*_PDB_MutableArray":
                searcher = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
                searcher.setUseMutableArrayPath(true);
                return searcher;
            case "IDA*_PDB_Bitboard":
                return new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
            default:
                throw new IllegalArgumentException("Unknown benchmark configuration: " + config);
        }
    }

    static String[] configOrder(int trial, int instanceId) {
        int offset = Math.floorMod((trial - 1) + (instanceId - 1), CONFIGS.length);
        String[] order = new String[CONFIGS.length];
        for (int i = 0; i < CONFIGS.length; i++) {
            order[i] = CONFIGS[(i + offset) % CONFIGS.length];
        }
        return order;
    }

    private static void runConfigSilent(AbstractSearcher searcher, Problem problem,
                                        ExecutorService executor, int timeoutSeconds) {
        Future<Deque<Node>> future = executor.submit(() -> searcher.search(problem));
        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            future.cancel(true);
        }
    }
    private static void runConfig(int trial, int instanceId, int executionOrder,
                                  String configName, AbstractSearcher searcher, Problem problem,
                                  ExecutorService executor, PrintWriter csvWriter) {
        System.out.printf("   [%d] %-24s -> ", executionOrder, configName);

        Future<TimedSearchResult> future = executor.submit(() -> {
            long startTime = System.nanoTime();
            try {
                Deque<Node> path = searcher.search(problem);
                return new TimedSearchResult(path, System.nanoTime() - startTime, false);
            } catch (OutOfMemoryError error) {
                return new TimedSearchResult(null, System.nanoTime() - startTime, true);
            }
        });

        try {
            TimedSearchResult timed = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (timed.path != null) {
                int depth = timed.path.size() - 1;
                long generated = searcher.nodesGenerated();
                long expanded = searcher.nodesExpanded();
                double ebf = calculateEBF(expanded, depth);
                double timeMs = timed.timeNs / 1_000_000.0;

                System.out.printf("[SUCCESS] Depth:%d | Search:%9.3f ms | Expanded:%,10d | EBF:%.3f%n",
                        depth, timeMs, expanded, ebf);
                csvWriter.printf(Locale.ROOT,
                        "%d,%d,%d,%s,Solved,%d,%d,%d,%d,%.6f,%.6f%n",
                        trial, instanceId, executionOrder, configName, depth, generated,
                        expanded, timed.timeNs, timeMs, ebf);
                recordResult(trial, configName, instanceId,
                        new TestResult(true, depth, timed.timeNs, generated, expanded, ebf));
            } else {
                String status = timed.outOfMemory ? "OOM" : "NoSolution";
                System.out.printf("[%s]%n", status.toUpperCase(Locale.ROOT));
                csvWriter.printf(Locale.ROOT, "%d,%d,%d,%s,%s,-1,%d,%d,%d,%.6f,-1%n",
                        trial, instanceId, executionOrder, configName, status,
                        searcher.nodesGenerated(), searcher.nodesExpanded(), timed.timeNs,
                        timed.timeNs / 1_000_000.0);
                recordResult(trial, configName, instanceId,
                        new TestResult(false, -1, timed.timeNs, searcher.nodesGenerated(),
                                searcher.nodesExpanded(), 0));
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            System.out.printf("[TIMEOUT] >%d s | Expanded:%,d%n",
                    TIMEOUT_SECONDS, searcher.nodesExpanded());
            csvWriter.printf(Locale.ROOT, "%d,%d,%d,%s,Timeout,-1,%d,%d,-1,-1,-1%n",
                    trial, instanceId, executionOrder, configName,
                    searcher.nodesGenerated(), searcher.nodesExpanded());
            recordResult(trial, configName, instanceId,
                    new TestResult(false, -1, -1, searcher.nodesGenerated(),
                            searcher.nodesExpanded(), 0));
        } catch (Throwable e) {
            future.cancel(true);
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            System.out.printf("[ERROR] %s%n", cause.getClass().getSimpleName());
            csvWriter.printf(Locale.ROOT, "%d,%d,%d,%s,Error,-1,%d,%d,-1,-1,-1%n",
                    trial, instanceId, executionOrder, configName,
                    searcher.nodesGenerated(), searcher.nodesExpanded());
            recordResult(trial, configName, instanceId,
                    new TestResult(false, -1, -1, searcher.nodesGenerated(),
                            searcher.nodesExpanded(), 0));
        }
        csvWriter.flush();
    }

    private static void recordResult(int trial, String config, int instanceId, TestResult result) {
        allTrialsData.get(trial).get(config).put(instanceId, result);
    }
    private static void printStatisticalSummary(int totalInstances) {
        Set<Integer> universallySolved = universallySolvedInstances(totalInstances);

        System.out.println("\n================================================================================================");
        System.out.printf("Statistical Summary (%d trials; per-instance median search time)%n", TRIALS);
        System.out.printf("Timing set: %d/%d instances solved by every configuration in every trial.%n",
                universallySolved.size(), totalInstances);
        System.out.println("================================================================================================");
        System.out.printf("%-25s | %-10s | %-13s | %-15s | %-15s | %-8s%n",
                "Configuration", "Complete", "Mean ms", "Median ms", "Mean Expanded", "Mean EBF");
        System.out.println("-".repeat(100));

        for (String config : CONFIGS) {
            int completeInstances = 0;
            for (int instanceId = 1; instanceId <= totalInstances; instanceId++) {
                if (solvedInEveryTrial(config, instanceId)) completeInstances++;
            }

            List<Double> instanceMedianTimes = new ArrayList<>();
            double totalExpanded = 0;
            double totalEbf = 0;
            int samples = 0;
            for (int instanceId : universallySolved) {
                instanceMedianTimes.add(medianTimeNs(config, instanceId) / 1_000_000.0);
                for (int trial = 1; trial <= TRIALS; trial++) {
                    TestResult result = allTrialsData.get(trial).get(config).get(instanceId);
                    totalExpanded += result.expandedNodes;
                    totalEbf += result.ebf;
                    samples++;
                }
            }

            String meanTime = instanceMedianTimes.isEmpty() ? "N/A" :
                    String.format(Locale.ROOT, "%.3f", mean(instanceMedianTimes));
            String medianTime = instanceMedianTimes.isEmpty() ? "N/A" :
                    String.format(Locale.ROOT, "%.3f", median(instanceMedianTimes));
            String meanExpanded = samples == 0 ? "N/A" :
                    String.format(Locale.ROOT, "%,.0f", totalExpanded / samples);
            String meanEbf = samples == 0 ? "N/A" :
                    String.format(Locale.ROOT, "%.3f", totalEbf / samples);

            System.out.printf(Locale.ROOT, "%-25s | %4d/%-5d | %-13s | %-15s | %-15s | %-8s%n",
                    config, completeInstances, totalInstances,
                    meanTime, medianTime, meanExpanded, meanEbf);
        }

        printConsistencyChecks(totalInstances);
        printPairedSpeedup("PDB OOP -> Mutable Array", "IDA*_PDB_OOP",
                "IDA*_PDB_MutableArray", universallySolved);
        printPairedSpeedup("Mutable Array -> Bitboard", "IDA*_PDB_MutableArray",
                "IDA*_PDB_Bitboard", universallySolved);
        printPairedSpeedup("PDB OOP -> Bitboard", "IDA*_PDB_OOP",
                "IDA*_PDB_Bitboard", universallySolved);
        System.out.println("================================================================================================\n");
    }

    private static Set<Integer> universallySolvedInstances(int totalInstances) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int instanceId = 1; instanceId <= totalInstances; instanceId++) {
            boolean solved = true;
            for (int trial = 1; trial <= TRIALS && solved; trial++) {
                for (String config : CONFIGS) {
                    TestResult test = allTrialsData.get(trial).get(config).get(instanceId);
                    if (test == null || !test.solved) {
                        solved = false;
                        break;
                    }
                }
            }
            if (solved) result.add(instanceId);
        }
        return result;
    }
    private static boolean solvedInEveryTrial(String config, int instanceId) {
        for (int trial = 1; trial <= TRIALS; trial++) {
            TestResult result = allTrialsData.get(trial).get(config).get(instanceId);
            if (result == null || !result.solved) return false;
        }
        return true;
    }

    private static double medianTimeNs(String config, int instanceId) {
        List<Double> times = new ArrayList<>(TRIALS);
        for (int trial = 1; trial <= TRIALS; trial++) {
            times.add((double) allTrialsData.get(trial).get(config).get(instanceId).timeNs);
        }
        return median(times);
    }

    private static void printConsistencyChecks(int totalInstances) {
        int jointlySolved = 0;
        int solutionLengthMismatches = 0;
        int pdbNodeMismatches = 0;

        for (int trial = 1; trial <= TRIALS; trial++) {
            for (int instanceId = 1; instanceId <= totalInstances; instanceId++) {
                int depth = -1;
                boolean allSolved = true;
                boolean depthMismatch = false;
                for (String config : CONFIGS) {
                    TestResult result = allTrialsData.get(trial).get(config).get(instanceId);
                    if (result == null || !result.solved) {
                        allSolved = false;
                        break;
                    }
                    if (depth == -1) depth = result.solutionLength;
                    else if (depth != result.solutionLength) depthMismatch = true;
                }
                if (!allSolved) continue;

                jointlySolved++;
                if (depthMismatch) solutionLengthMismatches++;

                TestResult oop = allTrialsData.get(trial).get("IDA*_PDB_OOP").get(instanceId);
                TestResult array = allTrialsData.get(trial).get("IDA*_PDB_MutableArray").get(instanceId);
                TestResult bit = allTrialsData.get(trial).get("IDA*_PDB_Bitboard").get(instanceId);
                if (oop.expandedNodes != array.expandedNodes || oop.expandedNodes != bit.expandedNodes ||
                        oop.generatedNodes != array.generatedNodes || oop.generatedNodes != bit.generatedNodes) {
                    pdbNodeMismatches++;
                }
            }
        }

        System.out.printf("Checks: %d jointly solved trial-instances; solution-length mismatches=%d; " +
                        "PDB node-count mismatches=%d.%n",
                jointlySolved, solutionLengthMismatches, pdbNodeMismatches);
    }

    private static void printPairedSpeedup(String label, String slower, String faster,
                                           Set<Integer> instances) {
        if (instances.isEmpty()) return;

        List<Double> ratios = new ArrayList<>(instances.size());
        double logSum = 0;
        for (int instanceId : instances) {
            double ratio = medianTimeNs(slower, instanceId) / medianTimeNs(faster, instanceId);
            ratios.add(ratio);
            logSum += Math.log(ratio);
        }
        double geometricMean = Math.exp(logSum / ratios.size());
        System.out.printf(Locale.ROOT, "%s paired speedup: geometric mean %.3fx; median %.3fx.%n",
                label, geometricMean, median(ratios));
    }
    private static double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.size();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) return sorted.get(middle);
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    static double calculateEBF(long expandedNodes, int depth) {
        if (depth == 0 || expandedNodes <= depth + 1) return 1.0;
        double low = 1.0, high = 100.0, epsilon = 1e-5;
        while (high - low > epsilon) {
            double mid = (low + high) / 2.0;
            double calcNodes = (Math.pow(mid, depth + 1) - 1) / (mid - 1);
            if (calcNodes < expandedNodes) low = mid;
            else high = mid;
        }
        return (low + high) / 2.0;
    }

    private static ArrayList<Problem> getProblems(EngineFeeder feeder, String inputFile) throws Exception {
        ArrayList<String> lines = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(inputFile), StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
            }
        }
        return feeder.getProblems(lines);
    }
}