package npuzzle.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

/**
 * Heuristic based on the Manhattan Distance.
 * Optimizes performance by pre-calculating tile coordinates to avoid
 * repeated integer division and modulo operations.
 */
public class ManhattanPredictor implements Predictor {
    private int[] rowLookup;
    private int[] colLookup;
    private int boardSize;
    private PuzzleBoard cachedGoal;

    private static int[] indexToRow;
    private static int[] indexToCol;

    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;

        if (rowLookup == null || !target.equals(cachedGoal)) {
            initializeLookupTable(target);
            cachedGoal = target;
        }

        return computeDistance(current);
    }

    private void initializeLookupTable(PuzzleBoard goal) {
        int[] goalTiles = goal.getPuzzleBoard();
        this.boardSize = goal.getSize();
        int len = goalTiles.length;

        this.rowLookup = new int[len];
        this.colLookup = new int[len];

        if (indexToRow == null || indexToRow.length != len) {
            indexToRow = new int[len];
            indexToCol = new int[len];
            for (int i = 0; i < len; i++) {
                indexToRow[i] = i / boardSize;
                indexToCol[i] = i % boardSize;
            }
        }

        for (int i = 0; i < len; i++) {
            int tile = goalTiles[i];
            if (tile != 0) {
                rowLookup[tile] = indexToRow[i];
                colLookup[tile] = indexToCol[i];
            }
        }
    }

    private int computeDistance(PuzzleBoard current) {
        int[] tiles = current.getPuzzleBoard();
        int distance = 0;

        for (int i = 0; i < tiles.length; i++) {
            int tile = tiles[i];
            if (tile != 0) {
                distance += Math.abs(indexToRow[i] - rowLookup[tile]) +
                        Math.abs(indexToCol[i] - colLookup[tile]);
            }
        }
        return distance;
    }
}