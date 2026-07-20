package npuzzle.solver;

import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import npuzzle.heuristic.ManhattanPredictor;
import npuzzle.model.NPuzzleProblem;
import npuzzle.model.PuzzleBoard;
import npuzzle.runner.PuzzleFeeder;
import npuzzle.solver.database.DisjointPatternDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Deque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdAStarTest {
    private static DisjointPatternDatabase pdb;

    private final PuzzleBoard goal3 = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});
    private final PuzzleBoard goal4 = new PuzzleBoard(4,
            new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0});
    private final PuzzleFeeder feeder = new PuzzleFeeder();

    @BeforeAll
    static void loadPatternDatabase() throws IOException {
        pdb = DisjointPatternDatabase.loadCompactDatabases();
    }

    @Test
    void solvedRootReturnsZeroMovePath() {
        IdAStar searcher = new IdAStar(feeder.getFrontier(EvaluationType.FULL), new ManhattanPredictor());
        NPuzzleProblem problem = new NPuzzleProblem(goal3, goal3, 3);

        Deque<Node> path = searcher.search(problem);

        assertEquals(1, path.size());
        assertEquals(goal3, path.getLast().getState());
        assertEquals(0, searcher.nodesExpanded());
        assertEquals(0, searcher.nodesGenerated());
    }

    @Test
    void oneMoveProblemEndsAtActualGoal() {
        PuzzleBoard start = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        IdAStar searcher = new IdAStar(feeder.getFrontier(EvaluationType.FULL), new ManhattanPredictor());
        NPuzzleProblem problem = new NPuzzleProblem(start, goal3, 3);

        Deque<Node> path = searcher.search(problem);

        assertEquals(2, path.size());
        assertEquals(goal3, path.getLast().getState());
    }

    @Test
    void generalHeuristicSupportsCustomGoal() {
        PuzzleBoard start = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});
        PuzzleBoard customGoal = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        IdAStar searcher = new IdAStar(feeder.getFrontier(EvaluationType.FULL), new ManhattanPredictor());
        NPuzzleProblem problem = new NPuzzleProblem(start, customGoal, 3);

        Deque<Node> path = searcher.search(problem);

        assertEquals(2, path.size());
        assertEquals(customGoal, path.getLast().getState());
    }

    @Test
    void pdbExecutionModesHaveIdenticalSearchSemantics() {
        PuzzleBoard start = new PuzzleBoard(4,
                new int[]{4, 8, 1, 3, 6, 9, 5, 12, 14, 10, 11, 0, 2, 15, 7, 13});
        NPuzzleProblem problem = new NPuzzleProblem(start, goal4, 4);

        IdAStar oop = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
        oop.setUseGeneralPath(true);
        Deque<Node> oopPath = oop.search(problem);

        IdAStar mutableArray = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
        mutableArray.setUseMutableArrayPath(true);
        Deque<Node> arrayPath = mutableArray.search(problem);

        IdAStar bitboard = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
        Deque<Node> bitboardPath = bitboard.search(problem);

        assertNotNull(oopPath);
        assertEquals(oopPath.size(), arrayPath.size());
        assertEquals(oopPath.size(), bitboardPath.size());
        assertEquals(goal4, oopPath.getLast().getState());
        assertEquals(goal4, arrayPath.getLast().getState());
        assertEquals(goal4, bitboardPath.getLast().getState());
        assertEquals(oop.nodesExpanded(), mutableArray.nodesExpanded());
        assertEquals(oop.nodesExpanded(), bitboard.nodesExpanded());
        assertEquals(oop.nodesGenerated(), mutableArray.nodesGenerated());
        assertEquals(oop.nodesGenerated(), bitboard.nodesGenerated());
    }
    @Test
    void unsolvableEarlyReturnResetsCountersOnReusedSearcher() {
        PuzzleBoard oneMove = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        PuzzleBoard unsolvable = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0});
        IdAStar searcher = new IdAStar(feeder.getFrontier(EvaluationType.FULL), new ManhattanPredictor());

        searcher.search(new NPuzzleProblem(oneMove, goal3, 3));
        Deque<Node> path = searcher.search(new NPuzzleProblem(unsolvable, goal3, 3));

        assertNull(path);
        assertEquals(0, searcher.nodesExpanded());
        assertEquals(0, searcher.nodesGenerated());
    }
    @Test
    void patternDatabaseRejectsNonCanonicalGoal() {
        PuzzleBoard customGoal = new PuzzleBoard(4,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0, 15});
        IdAStar searcher = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
        NPuzzleProblem problem = new NPuzzleProblem(customGoal, customGoal, 4);

        assertThrows(IllegalArgumentException.class, () -> searcher.search(problem));
    }

    @Test
    void pdbExecutionModesRemainEquivalentAcrossDeterministicShallowStates() {
        Random random = new Random(20260720L);

        for (int caseIndex = 0; caseIndex < 50; caseIndex++) {
            PuzzleBoard start = randomWalkBoard(random, 10 + random.nextInt(16));
            NPuzzleProblem problem = new NPuzzleProblem(start, goal4, 4);

            IdAStar oop = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
            oop.setUseGeneralPath(true);
            Deque<Node> oopPath = oop.search(problem);

            IdAStar mutableArray = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
            mutableArray.setUseMutableArrayPath(true);
            Deque<Node> arrayPath = mutableArray.search(problem);

            IdAStar bitboard = new IdAStar(feeder.getFrontier(EvaluationType.FULL), pdb);
            Deque<Node> bitboardPath = bitboard.search(problem);

            assertNotNull(oopPath);
            assertNotNull(arrayPath);
            assertNotNull(bitboardPath);
            assertEquals(oopPath.size(), arrayPath.size());
            assertEquals(oopPath.size(), bitboardPath.size());
            assertEquals(goal4, oopPath.getLast().getState());
            assertEquals(goal4, arrayPath.getLast().getState());
            assertEquals(goal4, bitboardPath.getLast().getState());
            assertEquals(oop.nodesExpanded(), mutableArray.nodesExpanded());
            assertEquals(oop.nodesExpanded(), bitboard.nodesExpanded());
            assertEquals(oop.nodesGenerated(), mutableArray.nodesGenerated());
            assertEquals(oop.nodesGenerated(), bitboard.nodesGenerated());
        }
    }

    private PuzzleBoard randomWalkBoard(Random random, int steps) {
        int[] tiles = goal4.getPuzzleBoard();
        int zeroPos = 15;
        int lastMove = -1;
        int[] offsets = {1, -1, 4, -4};
        int[] candidates = new int[4];

        for (int step = 0; step < steps; step++) {
            int candidateCount = 0;
            for (int direction = 0; direction < 4; direction++) {
                if (lastMove != -1 && (lastMove ^ 1) == direction) continue;
                if (direction == 0 && zeroPos % 4 == 3) continue;
                if (direction == 1 && zeroPos % 4 == 0) continue;
                if (direction == 2 && zeroPos >= 12) continue;
                if (direction == 3 && zeroPos < 4) continue;
                candidates[candidateCount++] = direction;
            }

            int direction = candidates[random.nextInt(candidateCount)];
            int nextZero = zeroPos + offsets[direction];
            tiles[zeroPos] = tiles[nextZero];
            tiles[nextZero] = 0;
            zeroPos = nextZero;
            lastMove = direction;
        }

        return new PuzzleBoard(4, tiles);
    }
}
