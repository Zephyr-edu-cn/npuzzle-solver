package npuzzle.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

/**
 * Manhattan Distance + Linear Conflict Heuristic.
 * An admissible heuristic that adds 2 moves for each tile that must leave
 * its current line to resolve all row/column order conflicts.
 */
public class LinearConflictPredictor implements Predictor {

    @Override
    public int heuristics(State state, State goal) {
        if (!(state instanceof PuzzleBoard) || !(goal instanceof PuzzleBoard)) {
            return 0;
        }

        PuzzleBoard currentState = (PuzzleBoard) state;
        PuzzleBoard goalState = (PuzzleBoard) goal;
        int size = currentState.getSize();
        int[] currentTiles = currentState.getPuzzleBoard();
        int[] goalTiles = goalState.getPuzzleBoard();

        int[] goalRows = new int[currentTiles.length];
        int[] goalCols = new int[currentTiles.length];
        for (int i = 0; i < goalTiles.length; i++) {
            int tile = goalTiles[i];
            if (tile != 0) {
                goalRows[tile] = i / size;
                goalCols[tile] = i % size;
            }
        }

        int manhattan = 0;
        int linearConflict = 0;

        // 1. Calculate base Manhattan distance
        for (int i = 0; i < currentTiles.length; i++) {
            int tile = currentTiles[i];
            if (tile == 0) continue;

            int currentRow = i / size;
            int currentCol = i % size;

            manhattan += Math.abs(currentRow - goalRows[tile]) + Math.abs(currentCol - goalCols[tile]);
        }

        // 2. For each row, count the minimum tiles to remove to make target columns increasing.
        for (int row = 0; row < size; row++) {
            int[] targetCols = new int[size];
            int count = 0;
            for (int col = 0; col < size; col++) {
                int tile = currentTiles[row * size + col];
                if (tile != 0 && goalRows[tile] == row) {
                    targetCols[count++] = goalCols[tile];
                }
            }
            linearConflict += 2 * minimumRemovalsForIncreasingOrder(targetCols, count);
        }

        // 3. For each column, count the minimum tiles to remove to make target rows increasing.
        for (int col = 0; col < size; col++) {
            int[] targetRows = new int[size];
            int count = 0;
            for (int row = 0; row < size; row++) {
                int tile = currentTiles[row * size + col];
                if (tile != 0 && goalCols[tile] == col) {
                    targetRows[count++] = goalRows[tile];
                }
            }
            linearConflict += 2 * minimumRemovalsForIncreasingOrder(targetRows, count);
        }

        return manhattan + linearConflict;
    }

    private int minimumRemovalsForIncreasingOrder(int[] values, int count) {
        if (count <= 1) return 0;

        int[] tails = new int[count];
        int length = 0;

        for (int i = 0; i < count; i++) {
            int value = values[i];
            int low = 0;
            int high = length;

            while (low < high) {
                int mid = (low + high) >>> 1;
                if (tails[mid] < value) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }

            tails[low] = value;
            if (low == length) length++;
        }

        return count - length;
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
