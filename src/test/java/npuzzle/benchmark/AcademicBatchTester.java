package npuzzle.benchmark;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.queue.Node;
import npuzzle.heuristic.LinearConflictPredictor;
import npuzzle.heuristic.ManhattanPredictor;
import npuzzle.solver.IdAStar;
import npuzzle.solver.database.DisjointPatternDatabase;

import java.io.*;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Scanner;
import java.util.concurrent.*;

/**
 * Academic Batch Benchmark Tool.
 * Automates ablation studies across configurations with strict timeout controls.
 * Calculates Effective Branching Factor (EBF) for heuristic evaluation.
 */
public class AcademicBatchTester {

    private static final String INPUT_FILE = "datasets/my_benchmark_100.txt";
    private static final String OUTPUT_CSV = "academic_results.csv";
    private static final int TIMEOUT_SECONDS = 60;

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("Initiating Academic Batch Benchmark");
        System.out.println("Timeout Threshold: " + TIMEOUT_SECONDS + " seconds/instance");
        System.out.println("==================================================\n");

        EngineFeeder feeder = (EngineFeeder) Class.forName("npuzzle.runner.PuzzleFeeder").getDeclaredConstructor().newInstance();
        ArrayList<Problem> problems = getProblems(feeder, INPUT_FILE);

        System.out.println("[INFO] Pre-loading heuristic databases...");
        DisjointPatternDatabase pdb = DisjointPatternDatabase.loadCompactDatabases();
        ManhattanPredictor manhattan = new ManhattanPredictor();
        LinearConflictPredictor linearConflict = new LinearConflictPredictor();

        PrintWriter csvWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_CSV), "UTF-8"));
        csvWriter.println("Instance_ID,Configuration,Status,Solution_Length,Generated_Nodes,Expanded_Nodes,Time_ms,EBF");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        for (int i = 0; i < problems.size(); i++) {
            Problem p = problems.get(i);
            int instanceId = i + 1;
            System.out.println("\n[INFO] Processing Instance [" + instanceId + "/" + problems.size() + "]");

            runConfig(instanceId, "IDA*_Manhattan", new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), manhattan), p, executor, csvWriter);
            runConfig(instanceId, "IDA*_LinearConflict", new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), linearConflict), p, executor, csvWriter);

            IdAStar pdbOop = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb);
            pdbOop.setUseGeneralPath(true);
            runConfig(instanceId, "IDA*_PDB_OOP", pdbOop, p, executor, csvWriter);

            IdAStar pdbBitboard = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb);
            pdbBitboard.setUseGeneralPath(false);
            runConfig(instanceId, "IDA*_PDB_Bitboard", pdbBitboard, p, executor, csvWriter);
        }

        executor.shutdownNow();
        csvWriter.close();
        System.out.println("\n[DONE] Benchmark complete. Results exported to: " + OUTPUT_CSV);
    }

    private static void runConfig(int instanceId, String configName, IdAStar searcher, Problem problem, ExecutorService executor, PrintWriter csvWriter) {
        System.out.printf("   ├─ Config: %-22s -> ", configName);

        Callable<Deque<Node>> task = () -> searcher.search(problem);
        Future<Deque<Node>> future = executor.submit(task);

        long startTime = System.currentTimeMillis();
        try {
            Deque<Node> path = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long timeMs = System.currentTimeMillis() - startTime;

            if (path != null) {
                int depth = path.size() - 1;
                long generated = searcher.nodesGenerated();
                long expanded = searcher.nodesExpanded();
                double ebf = calculateEBF(generated, depth);

                System.out.printf("[SUCCESS] Depth:%d | Time:%5d ms | Nodes:%,10d | EBF:%.3f\n", depth, timeMs, generated, ebf);
                csvWriter.printf("%d,%s,Solved,%d,%d,%d,%d,%.3f\n", instanceId, configName, depth, generated, expanded, timeMs, ebf);
            } else {
                System.out.println("[FAIL] (No Solution)");
                csvWriter.printf("%d,%s,NoSolution,-1,-1,-1,-1,-1\n", instanceId, configName);
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            System.out.printf("[TIMEOUT] (>%d sec) | Nodes Generated: %,d\n", TIMEOUT_SECONDS, searcher.nodesGenerated());
            csvWriter.printf("%d,%s,Timeout,-1,%d,%d,>%d,-1\n", instanceId, configName, searcher.nodesGenerated(), searcher.nodesExpanded(), TIMEOUT_SECONDS * 1000);
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            csvWriter.printf("%d,%s,Error,-1,-1,-1,-1,-1\n", instanceId, configName);
        }
        csvWriter.flush();
    }

    private static double calculateEBF(long nodes, int depth) {
        if (depth == 0) return 1.0;
        if (nodes <= depth + 1) return 1.0;

        double low = 1.0;
        double high = 5.0;
        double epsilon = 1e-5;

        while (high - low > epsilon) {
            double mid = (low + high) / 2.0;
            double calcNodes = (Math.pow(mid, depth + 1) - 1) / (mid - 1);
            if (calcNodes < nodes) {
                low = mid;
            } else {
                high = mid;
            }
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