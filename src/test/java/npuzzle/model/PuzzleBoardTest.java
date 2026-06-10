package npuzzle.model;

import core.problem.Action;
import core.problem.State;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PuzzleBoardTest {

    @Test
    void constructorAndGetterDefensivelyCopyTiles() {
        int[] tiles = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        PuzzleBoard board = new PuzzleBoard(3, tiles);

        tiles[0] = 99;
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0}, board.getPuzzleBoard());

        int[] snapshot = board.getPuzzleBoard();
        snapshot[1] = 88;
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0}, board.getPuzzleBoard());
    }

    @Test
    void reportsZeroPositionAndCornerActions() {
        PuzzleBoard board = new PuzzleBoard(3, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8});

        assertEquals(0, board.getZeroPos());

        Set<Action> actions = new HashSet<>();
        for (Object action : board.actions()) {
            actions.add((Action) action);
        }

        assertEquals(Set.of(PuzzleAction.DOWN, PuzzleAction.RIGHT), actions);
    }

    @Test
    void nextAppliesValidMove() {
        PuzzleBoard board = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 0, 5, 6, 7, 8});

        State next = board.next(PuzzleAction.RIGHT);

        assertEquals(
                new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 0, 6, 7, 8}),
                next
        );
    }

    @Test
    void equalBoardsHaveSameHashCode() {
        PuzzleBoard a = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});
        PuzzleBoard b = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
