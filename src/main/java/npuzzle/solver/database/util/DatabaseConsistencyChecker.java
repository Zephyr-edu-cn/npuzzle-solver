package npuzzle.solver.database.util;

import npuzzle.model.PuzzleBoard;
import npuzzle.solver.database.DisjointPatternDatabase;

/**
 * Utility to verify the integrity and consistency of the loaded Pattern Database.
 * Ensures that the heuristic cost of the goal state strictly evaluates to 0.
 */
public class DatabaseConsistencyChecker {
    public static void main(String[] args) {
        System.out.println("=== Database Consistency Check ===");

        try {
            PuzzleBoard goalState = createStandardGoalState();
            System.out.println("Reference Goal State:");
            goalState.draw();

            PuzzleBoard testState = createStandardGoalState();
            DisjointPatternDatabase db = DisjointPatternDatabase.loadCompactDatabases();

            int heuristic = db.heuristics(testState, goalState);
            System.out.printf("Heuristic value of goal state: %d (Expected: 0)\n", heuristic);

            if (heuristic == 0) {
                System.out.println("[PASS] Database consistency verified.");
            } else {
                System.err.println("[FAIL] Database consistency violated.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static PuzzleBoard createStandardGoalState() {
        int[] goalTiles = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        return new PuzzleBoard(4, goalTiles);
    }
}