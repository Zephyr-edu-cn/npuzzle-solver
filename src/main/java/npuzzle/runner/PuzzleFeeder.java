package npuzzle.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import npuzzle.heuristic.*;
import npuzzle.model.NPuzzleProblem;
import npuzzle.model.PuzzleBoard;
import npuzzle.queue.PqFrontier;
import npuzzle.solver.database.DisjointPatternDatabase;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Factory class responsible for initializing problems, frontiers, and predictors
 * specific to the N-Puzzle domain.
 */
public class PuzzleFeeder extends EngineFeeder {

    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        ArrayList<Problem> problems = new ArrayList<>();
        if (problemLines == null || problemLines.isEmpty()) {
            System.err.println("Warning: No problem instances provided.");
            return problems;
        }

        for (String line : problemLines) {
            Problem problem = parseProblem(line);
            if (problem != null) {
                problems.add(problem);
            }
        }
        return problems;
    }

    /**
     * Parses a string definition into an NPuzzleProblem instance.
     * Expected format: "size initial_state_array goal_state_array"
     */
    private Problem parseProblem(String line) {
        try {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) return null;

            String[] parts = line.split("\\s+");
            if (parts.length >= 3) {
                int size = Integer.parseInt(parts[0].trim());
                int boardCapacity = size * size;

                // Construct comma-separated strings for parsing
                StringBuilder initialStr = new StringBuilder();
                StringBuilder goalStr = new StringBuilder();

                for (int i = 1; i <= boardCapacity; i++) {
                    initialStr.append(parts[i]).append(",");
                    goalStr.append(parts[i + boardCapacity]).append(",");
                }

                int[] initialTiles = parseTiles(initialStr.toString());
                int[] goalTiles = parseTiles(goalStr.toString());

                if (initialTiles.length != boardCapacity || goalTiles.length != boardCapacity) {
                    System.err.println("Error: Tile array length mismatch for size " + size);
                    return null;
                }

                PuzzleBoard initialState = new PuzzleBoard(size, initialTiles);
                PuzzleBoard goalState = new PuzzleBoard(size, goalTiles);
                return new NPuzzleProblem(initialState, goalState, size);
            }
        } catch (Exception e) {
            System.err.println("Error parsing problem line: " + line);
            e.printStackTrace();
        }
        return null;
    }

    private int[] parseTiles(String tileStr) {
        tileStr = tileStr.replaceAll("[\\[\\]\\s]", "");
        String[] tileStrings = tileStr.split(",");
        int[] tiles = new int[tileStrings.length];

        for (int i = 0; i < tileStrings.length; i++) {
            tiles[i] = Integer.parseInt(tileStrings[i].trim());
        }
        return tiles;
    }

    @Override
    public Frontier getFrontier(EvaluationType type) {
        return new PqFrontier(core.solver.queue.Node.evaluator(type));
    }

    @Override
    public Predictor getPredictor(HeuristicType type) {
        switch (type) {
            case MANHATTAN:
                return new ManhattanPredictor();
            case MISPLACED:
                return new MisplacedPredictor();
            case HAMMING:
                return new HammingPredictor();
            case LINEAR_CONFLICT:
                return new LinearConflictPredictor();
            case DISJOINT_PATTERN:
                try {
                    return DisjointPatternDatabase.loadCompactDatabases();
                } catch (IOException e) {
                    System.err.println("Warning: PDB load failed. Falling back to Manhattan Distance. " + e.getMessage());
                    return new ManhattanPredictor();
                }
            default:
                System.err.println("Warning: Unsupported heuristic type. Defaulting to Manhattan.");
                return new ManhattanPredictor();
        }
    }

    @Override
    public AbstractSearcher getEmptyDistanceAStar(HeuristicType type) {
        Predictor predictor = new EmptyDistancePredictor();
        Frontier frontier = getFrontier(EvaluationType.FULL);
        return new BestFirstSearcher(frontier, predictor);
    }

    public AbstractSearcher getPatternDBAStar(HeuristicType type) {
        try {
            Predictor patternDB = DisjointPatternDatabase.loadCompactDatabases();
            Frontier frontier = getFrontier(EvaluationType.FULL);
            return new BestFirstSearcher(frontier, patternDB);
        } catch (IOException e) {
            System.err.println("Warning: PDB load failed. Falling back to A* with Manhattan.");
            return getAStar(type);
        }
    }
}