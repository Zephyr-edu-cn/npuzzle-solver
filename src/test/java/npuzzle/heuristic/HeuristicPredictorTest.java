package npuzzle.heuristic;

import npuzzle.model.PuzzleBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeuristicPredictorTest {

    private final PuzzleBoard goal3 = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});

    @Test
    void basicHeuristicsAreZeroOnGoalState() {
        assertEquals(0, new ManhattanPredictor().heuristics(goal3, goal3));
        assertEquals(0, new MisplacedPredictor().heuristics(goal3, goal3));
        assertEquals(0, new HammingPredictor().heuristics(goal3, goal3));
        assertEquals(0, new LinearConflictPredictor().heuristics(goal3, goal3));
    }

    @Test
    void oneMoveAwayStateHasExpectedBasicHeuristicValues() {
        PuzzleBoard oneMoveAway = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});

        assertEquals(1, new ManhattanPredictor().heuristics(oneMoveAway, goal3));
        assertEquals(1, new MisplacedPredictor().heuristics(oneMoveAway, goal3));
        assertEquals(1, new HammingPredictor().heuristics(oneMoveAway, goal3));
    }

    @Test
    void linearConflictAddsPenaltyForReversedTilesInGoalRow() {
        PuzzleBoard rowConflict = new PuzzleBoard(3, new int[]{2, 1, 3, 4, 5, 6, 7, 8, 0});

        assertEquals(4, new LinearConflictPredictor().heuristics(rowConflict, goal3));
    }

    @Test
    void manhattanCoordinateCacheIsIsolatedBetweenBoardSizes() {
        ManhattanPredictor predictor3 = new ManhattanPredictor();
        PuzzleBoard oneMove3 = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        assertEquals(1, predictor3.heuristics(oneMove3, goal3));

        ManhattanPredictor predictor4 = new ManhattanPredictor();
        PuzzleBoard goal4 = new PuzzleBoard(4,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0});
        PuzzleBoard oneMove4 = new PuzzleBoard(4,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0, 15});
        assertEquals(1, predictor4.heuristics(oneMove4, goal4));

        assertEquals(1, predictor3.heuristics(oneMove3, goal3));
    }
}
