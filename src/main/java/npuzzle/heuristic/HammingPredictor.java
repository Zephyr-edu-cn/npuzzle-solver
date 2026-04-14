package npuzzle.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

/**
 * Hamming Distance Heuristic.
 * Evaluates the number of tiles out of place. Functionally equivalent to
 * the Misplaced Tiles heuristic in the context of the N-Puzzle problem.
 */
public class HammingPredictor implements Predictor {
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;
        int[] currentTiles = current.getPuzzleBoard();
        int[] goalTiles = target.getPuzzleBoard();
        int distance = 0;

        for (int i = 0; i < currentTiles.length; i++) {
            if (currentTiles[i] != 0 && currentTiles[i] != goalTiles[i]) {
                distance++;
            }
        }

        return distance;
    }
}