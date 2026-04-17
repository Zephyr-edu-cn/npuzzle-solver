package npuzzle.model;

import core.problem.Action;
import core.problem.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable state representation for the N-Puzzle board.
 */
public class PuzzleBoard extends State {
    private final int[] tiles;
    private final int size;
    private int memoizedHash = 0;

    public PuzzleBoard(int size, int[] tiles) {
        this.tiles = tiles.clone();
        this.size = size;
    }

    public int[] getPuzzleBoard() { return tiles.clone(); }
    public int getSize() { return size; }
    public int getZeroPos() {
        for(int i = 0; i < tiles.length; i++) if(tiles[i] == 0) return i;
        return -1;
    }

    @Override
    public State next(Action action) {
        PuzzleAction a = (PuzzleAction) action;
        int p = getZeroPos();
        int target = (p / size + a.getDeltaRow()) * size + (p % size + a.getDeltaCol());
        int[] nextTiles = tiles.clone();
        nextTiles[p] = tiles[target];
        nextTiles[target] = 0;
        return new PuzzleBoard(size, nextTiles);
    }

    @Override
    public Iterable<? extends Action> actions() {
        List<PuzzleAction> actions = new ArrayList<>();
        int zeroPos = getZeroPos();
        int zeroRow = zeroPos / size;
        int zeroCol = zeroPos % size;

        /*检查四个方向并将可用方向填入actions中*/
        if(zeroRow > 0)actions.add(PuzzleAction.UP);
        if(zeroRow < size - 1)actions.add(PuzzleAction.DOWN);
        if(zeroCol > 0)actions.add(PuzzleAction.LEFT);
        if(zeroCol < size - 1)actions.add(PuzzleAction.RIGHT);

        return actions;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PuzzleBoard)) return false;
        PuzzleBoard that = (PuzzleBoard) obj;
        return size == that.size && Arrays.equals(tiles, that.tiles);
    }

    @Override
    public int hashCode() {
        if (memoizedHash == 0) memoizedHash = 31 * size + Arrays.hashCode(tiles);
        return memoizedHash;
    }

    @Override
    public void draw() {
        System.out.println("-------------");
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int tile = tiles[i * size + j];
                if (tile == 0) {
                    System.out.print("   ");
                } else {
                    System.out.printf("%2d ", tile);
                }
            }
            System.out.println();
        }
        System.out.println("-------------");
    }
}