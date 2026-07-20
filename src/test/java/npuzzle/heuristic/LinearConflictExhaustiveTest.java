package npuzzle.heuristic;

import npuzzle.model.PuzzleBoard;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearConflictExhaustiveTest {
    private static final int SIZE = 3;
    private static final int[] OFFSETS = {1, -1, SIZE, -SIZE};
    private static final PuzzleBoard GOAL = new PuzzleBoard(
            SIZE, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});

    @Test
    void linearConflictIsAdmissibleAndConsistentForAllReachableEightPuzzleStates() {
        LinearConflictPredictor predictor = new LinearConflictPredictor();
        long goal = pack(GOAL.getPuzzleBoard());
        Map<Long, Integer> distances = new HashMap<>(250_000);
        ArrayDeque<Long> queue = new ArrayDeque<>(200_000);
        distances.put(goal, 0);
        queue.add(goal);

        while (!queue.isEmpty()) {
            long state = queue.removeFirst();
            int exactDistance = distances.get(state);
            int zeroPos = zeroPosition(state);
            int heuristic = heuristic(predictor, state);
            assertTrue(heuristic <= exactDistance,
                    () -> "Overestimate at state " + Long.toUnsignedString(state));

            for (int direction = 0; direction < 4; direction++) {
                if (direction == 0 && zeroPos % SIZE == SIZE - 1) continue;
                if (direction == 1 && zeroPos % SIZE == 0) continue;
                if (direction == 2 && zeroPos >= SIZE * (SIZE - 1)) continue;
                if (direction == 3 && zeroPos < SIZE) continue;

                int nextZero = zeroPos + OFFSETS[direction];
                long next = swap(state, zeroPos, nextZero);
                int nextHeuristic = heuristic(predictor, next);
                assertTrue(heuristic <= 1 + nextHeuristic,
                        () -> "Inconsistency at state " + Long.toUnsignedString(state));

                if (!distances.containsKey(next)) {
                    distances.put(next, exactDistance + 1);
                    queue.addLast(next);
                }
            }
        }

        assertEquals(181_440, distances.size());
    }

    private static int heuristic(LinearConflictPredictor predictor, long state) {
        return predictor.heuristics(new PuzzleBoard(SIZE, unpack(state)), GOAL);
    }

    private static long pack(int[] tiles) {
        long packed = 0;
        for (int i = 0; i < tiles.length; i++) {
            packed |= (long) tiles[i] << (i * 4);
        }
        return packed;
    }

    private static int[] unpack(long state) {
        int[] tiles = new int[SIZE * SIZE];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = (int) ((state >>> (i * 4)) & 0xF);
        }
        return tiles;
    }

    private static int zeroPosition(long state) {
        for (int i = 0; i < SIZE * SIZE; i++) {
            if (((state >>> (i * 4)) & 0xF) == 0) return i;
        }
        throw new IllegalStateException("Packed board has no blank tile.");
    }

    private static long swap(long state, int first, int second) {
        int firstShift = first * 4;
        int secondShift = second * 4;
        long firstValue = (state >>> firstShift) & 0xF;
        long secondValue = (state >>> secondShift) & 0xF;
        long mask = (0xFL << firstShift) | (0xFL << secondShift);
        return (state & ~mask) | (firstValue << secondShift) | (secondValue << firstShift);
    }
}
