package npuzzle.model;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;
import java.util.Deque;

/**
 * Model class for the N-Puzzle problem instance.
 * Implements solvability verification using inversion counting.
 */
public class NPuzzleProblem extends Problem {
    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    @Override
    public boolean solvable() {
        return checkSolvability((PuzzleBoard) initialState, (PuzzleBoard) goal);
    }

    private boolean checkSolvability(PuzzleBoard start, PuzzleBoard goal) {
        int startInversions = countInversions(start.getPuzzleBoard());
        int goalInversions = countInversions(goal.getPuzzleBoard());

        if (size % 2 != 0) {
            return (startInversions % 2) == (goalInversions % 2);
        } else {
            int startBlankRow = start.getZeroPos() / size;
            int goalBlankRow = goal.getZeroPos() / size;
            return (startInversions % 2) == ((goalInversions + Math.abs(startBlankRow - goalBlankRow)) % 2);
        }
    }

    private int countInversions(int[] board) {
        int[] flat = new int[board.length - 1];
        int k = 0;
        for (int tile : board) if (tile != 0) flat[k++] = tile;
        return mergeSortAndCount(flat, 0, flat.length - 1);
    }

    private int mergeSortAndCount(int[] arr, int left, int right) {
        int count = 0;
        if (left < right) {
            int mid = left + (right - left) / 2;
            count += mergeSortAndCount(arr, left, mid);
            count += mergeSortAndCount(arr, mid + 1, right);
            count += mergeAndCount(arr, left, mid, right);
        }
        return count;
    }

    private int mergeAndCount(int[] arr, int left, int mid, int right) {
        int[] leftArr = new int[mid - left + 1];
        int[] rightArr = new int[right - mid];
        System.arraycopy(arr, left, leftArr, 0, leftArr.length);
        System.arraycopy(arr, mid + 1, rightArr, 0, rightArr.length);

        int i = 0, j = 0, k = left, swaps = 0;
        while (i < leftArr.length && j < rightArr.length) {
            if (leftArr[i] <= rightArr[j]) arr[k++] = leftArr[i++];
            else { arr[k++] = rightArr[j++]; swaps += (mid + 1) - (left + i); }
        }
        while (i < leftArr.length) arr[k++] = leftArr[i++];
        while (j < rightArr.length) arr[k++] = rightArr[j++];
        return swaps;
    }

    @Override
    public int stepCost(State state, Action action) { return 1; }

    @Override
    public boolean applicable(State state, Action action) {
        if (!(state instanceof PuzzleBoard) || !(action instanceof PuzzleAction)) return false;
        PuzzleBoard board = (PuzzleBoard) state;
        PuzzleAction puzzleAction = (PuzzleAction) action;
        int p = board.getZeroPos();
        int r = p / size, c = p % size;

        switch (puzzleAction.getDirection()) {
            case UP: return r > 0;
            case DOWN: return r < size - 1;
            case LEFT: return c > 0;
            case RIGHT: return c < size - 1;
            default: return false;
        }
    }

    @Override
    public void showSolution(Deque<Node> path) {
        // Reduced logging verbosity for clean production output
    }
}