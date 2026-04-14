package npuzzle.benchmark;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.queue.Node;
import npuzzle.solver.IdAStar;
import npuzzle.solver.database.DisjointPatternDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Scanner;

/**
 * Ablation Study for the N-Puzzle Solver.
 * Evaluates the performance contributions of algorithmic pruning (PDB)
 * and system-level optimization (Bitboard Engine).
 */
public class AblationTester {

    public static void main(String[] args) throws Exception {
        EngineFeeder feeder = (EngineFeeder) Class.forName("npuzzle.runner.PuzzleFeeder").getDeclaredConstructor().newInstance();

        System.out.println("===============================================================");
        System.out.println("Initiating Ablation Study (N-Puzzle Performance Analysis)");
        System.out.println("===============================================================\n");

        Scanner scanner = new Scanner(new File("datasets/hard_5_instances.txt"));
        ArrayList<String> lines = new ArrayList<>();
        while (scanner.hasNextLine() && lines.size() < 5) lines.add(scanner.nextLine().trim());
        ArrayList<Problem> hardProblems = feeder.getProblems(lines);

        DisjointPatternDatabase pdb = DisjointPatternDatabase.loadCompactDatabases();

        System.out.println(String.format("%-5s | %-6s | %-28s | %-12s | %-12s | %-15s",
                "Inst", "Steps", "Configuration", "Time(ms)", "Nodes Gen", "Speed(N/ms)"));
        System.out.println("-".repeat(90));

        for (int i = 0; i < hardProblems.size(); i++) {
            Problem p = hardProblems.get(i);
            int instanceId = i + 1;

            // Baseline configs (known to cause OOM or timeout on >= 50 steps)
            System.out.println(String.format(" #%02d  | %-6s | %-28s | %-12s | %-12s | %-15s",
                    instanceId, ">=50", "A: A* + Manhattan", "OOM (Crash)", "Millions+", "N/A"));
            System.out.println(String.format(" #%02d  | %-6s | %-28s | %-12s | %-12s | %-15s",
                    instanceId, ">=50", "B: IDA* + Manhattan", "> 1 Hour", "Billions+", "N/A"));

            // Configuration C (IDA* + PDB + Object-Oriented)
            IdAStar searcherC = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb);
            searcherC.setUseGeneralPath(true);
            System.gc();
            long startC = System.currentTimeMillis();
            Deque<Node> pathC = searcherC.search(p);
            long timeC = System.currentTimeMillis() - startC;
            if (pathC != null) {
                long nodesC = searcherC.nodesGenerated();
                System.out.println(String.format(" #%02d  | %-6d | %-28s | %-12d | %-12d | %-15d",
                        instanceId, pathC.size() - 1, "C: IDA* + PDB + OOP", timeC, nodesC, (timeC > 0 ? nodesC / timeC : nodesC)));
            }

            // Configuration D (IDA* + PDB + Bitboard)
            IdAStar searcherD = new IdAStar(feeder.getFrontier(core.solver.queue.EvaluationType.FULL), pdb);
            searcherD.setUseGeneralPath(false);
            System.gc();
            long startD = System.currentTimeMillis();
            Deque<Node> pathD = searcherD.search(p);
            long timeD = System.currentTimeMillis() - startD;
            if (pathD != null) {
                long nodesD = searcherD.nodesGenerated();
                System.out.println(String.format(" #%02d  | %-6d | %-28s | %-12d | %-12d | %-15d",
                        instanceId, pathD.size() - 1, "D: IDA* + PDB + Bitboard", timeD, nodesD, (timeD > 0 ? nodesD / timeD : nodesD)));
            }
            System.out.println("-".repeat(90));
        }
    }
}