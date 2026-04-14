package npuzzle.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

/**
 * Manhattan Distance + Linear Conflict Heuristic.
 * An admissible heuristic that adds a penalty of 2 moves for every pair
 * of tiles that are in their target row or column but in a reversed order.
 */
public class LinearConflictPredictor implements Predictor {

    @Override
    public int heuristics(State state, State goal) {
        if (!(state instanceof PuzzleBoard) || !(goal instanceof PuzzleBoard)) {
            return 0;
        }

        PuzzleBoard currentState = (PuzzleBoard) state;
        int size = currentState.getSize();
        int[] currentTiles = currentState.getPuzzleBoard();

        int manhattan = 0;
        int linearConflict = 0;

        // 1. Calculate base Manhattan distance
        for (int i = 0; i < currentTiles.length; i++) {
            int tile = currentTiles[i];
            if (tile == 0) continue;

            int goalPos = tile - 1;
            int currentRow = i / size;
            int currentCol = i % size;
            int goalRow = goalPos / size;
            int goalCol = goalPos % size;

            manhattan += Math.abs(currentRow - goalRow) + Math.abs(currentCol - goalCol);
        }

        // 2. Calculate Linear Conflict along rows
        for (int row = 0; row < size; row++) {
            for (int col1 = 0; col1 < size - 1; col1++) {
                int pos1 = row * size + col1;
                int tile1 = currentTiles[pos1];
                if (tile1 == 0) continue;

                int goalRow1 = (tile1 - 1) / size;

                if (goalRow1 == row) {
                    for (int col2 = col1 + 1; col2 < size; col2++) {
                        int pos2 = row * size + col2;
                        int tile2 = currentTiles[pos2];
                        if (tile2 == 0) continue;

                        int goalRow2 = (tile2 - 1) / size;

                        if (goalRow2 == row) {
                            int goalCol1 = (tile1 - 1) % size;
                            int goalCol2 = (tile2 - 1) % size;
                            // Conflict occurs if they are inverted relative to their goals
                            if (goalCol1 > goalCol2) {
                                linearConflict += 2;
                            }
                        }
                    }
                }
            }
        }

        // 3. Calculate Linear Conflict along columns
        for (int col = 0; col < size; col++) {
            for (int row1 = 0; row1 < size - 1; row1++) {
                int pos1 = row1 * size + col;
                int tile1 = currentTiles[pos1];
                if (tile1 == 0) continue;

                int goalCol1 = (tile1 - 1) % size;

                if (goalCol1 == col) {
                    for (int row2 = row1 + 1; row2 < size; row2++) {
                        int pos2 = row2 * size + col;
                        int tile2 = currentTiles[pos2];
                        if (tile2 == 0) continue;

                        int goalCol2 = (tile2 - 1) % size;

                        if (goalCol2 == col) {
                            int goalRow1 = (tile1 - 1) / size;
                            int goalRow2 = (tile2 - 1) / size;
                            // Conflict occurs if they are inverted relative to their goals
                            if (goalRow1 > goalRow2) {
                                linearConflict += 2;
                            }
                        }
                    }
                }
            }
        }

        return manhattan + linearConflict;
    }

    @Override
    public boolean supportsEncoding() {
        return false;
    }

    @Override
    public long encodeState(State state, State goal) {
        return 0;
    }
}