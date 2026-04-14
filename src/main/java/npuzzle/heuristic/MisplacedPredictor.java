package npuzzle.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

/**
 * Number of Misplaced Tiles Heuristic.
 * An admissible heuristic that counts the number of tiles (excluding the blank)
 * that are not in their target positions.
 */
public class MisplacedPredictor implements Predictor {
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;
        int[] currentTiles = current.getPuzzleBoard();
        int[] goalTiles = target.getPuzzleBoard();
        int misplacedCount = 0;

        for (int i = 0; i < currentTiles.length; i++) {
            // Exclude the blank tile (0) from the misplaced count
            if (currentTiles[i] != 0 && currentTiles[i] != goalTiles[i]) {
                misplacedCount++;
            }
        }

        return misplacedCount;
    }
}