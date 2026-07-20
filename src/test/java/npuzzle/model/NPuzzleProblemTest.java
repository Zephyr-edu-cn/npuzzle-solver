package npuzzle.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NPuzzleProblemTest {

    private final PuzzleBoard goal3 = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});

    @Test
    void goalBoardIsSolvableAndRecognizedAsGoal() {
        NPuzzleProblem problem = new NPuzzleProblem(goal3, goal3, 3);

        assertTrue(problem.solvable());
        assertTrue(problem.goal(goal3));
    }

    @Test
    void swappedPairInOddPuzzleIsUnsolvable() {
        PuzzleBoard unsolvable = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0});
        NPuzzleProblem problem = new NPuzzleProblem(unsolvable, goal3, 3);

        assertFalse(problem.solvable());
    }

    @Test
    void applicableRejectsMovesOutsideBoard() {
        PuzzleBoard topLeftBlank = new PuzzleBoard(3, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8});
        NPuzzleProblem problem = new NPuzzleProblem(topLeftBlank, goal3, 3);

        assertFalse(problem.applicable(topLeftBlank, PuzzleAction.UP));
        assertFalse(problem.applicable(topLeftBlank, PuzzleAction.LEFT));
        assertTrue(problem.applicable(topLeftBlank, PuzzleAction.DOWN));
        assertTrue(problem.applicable(topLeftBlank, PuzzleAction.RIGHT));
    }

    @Test
    void rejectsInvalidTilePermutation() {
        PuzzleBoard duplicateTile = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 7, 0});

        assertThrows(IllegalArgumentException.class,
                () -> new NPuzzleProblem(duplicateTile, goal3, 3));
    }

    @Test
    void rejectsMismatchedDeclaredSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new NPuzzleProblem(goal3, goal3, 4));
    }
    @Test
    void stepCostIsUnitCost() {
        NPuzzleProblem problem = new NPuzzleProblem(goal3, goal3, 3);

        assertEquals(1, problem.stepCost(goal3, PuzzleAction.LEFT));
    }
}
