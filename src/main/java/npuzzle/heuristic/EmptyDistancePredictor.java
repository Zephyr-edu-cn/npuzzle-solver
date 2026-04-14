package npuzzle.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

/**
 * Blank Tile Distance Heuristic.
 * Calculates the Manhattan distance of solely the blank tile to its target position.
 * Note: This is highly inadmissible and typically used only for specific relaxed problem models.
 */
public class EmptyDistancePredictor implements Predictor {
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;
        int size = current.getSize();

        int currentZeroPos = current.getZeroPos();
        int goalZeroPos = target.getZeroPos();

        int currentRow = currentZeroPos / size;
        int currentCol = currentZeroPos % size;
        int goalRow = goalZeroPos / size;
        int goalCol = goalZeroPos % size;

        return Math.abs(currentRow - goalRow) + Math.abs(currentCol - goalCol);
    }
}