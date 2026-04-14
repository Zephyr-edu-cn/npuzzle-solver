package npuzzle.solver;

import core.problem.Problem;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleAction;
import npuzzle.model.PuzzleBoard;
import npuzzle.solver.database.DisjointPatternDatabase;

import java.util.Deque;

/**
 * Iterative Deepening A* (IDA*) search algorithm.
 * Provides two execution modes:
 * 1. Bitboard Mode: Zero-allocation path for 4x4 boards with PDB.
 * 2. General Mode: Object-oriented path for arbitrary board dimensions.
 */
public class IdAStar extends AbstractSearcher {
    private DisjointPatternDatabase pdb;
    private final Predictor predictor;

    private boolean found = false;
    private byte[] solutionPath;
    private int solutionLength;
    private boolean useGeneralPath = false;

    private static final PuzzleAction[] ACTIONS = {
            PuzzleAction.RIGHT, PuzzleAction.LEFT, PuzzleAction.DOWN, PuzzleAction.UP
    };

    private int boardSize;
    private int[] moveOffsets;

    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        this.predictor = predictor;
        if (predictor instanceof DisjointPatternDatabase) {
            this.pdb = (DisjointPatternDatabase) predictor;
        }
    }

    public void setUseGeneralPath(boolean useGeneralPath) {
        this.useGeneralPath = useGeneralPath;
    }

    @Override
    public Deque<Node> search(Problem problem) {
        if (!problem.solvable()) return null;

        nodesExpanded = 0;
        nodesGenerated = 0;
        found = false;

        PuzzleBoard root = (PuzzleBoard) problem.root(predictor).getState();
        this.boardSize = root.getSize();
        this.moveOffsets = new int[]{1, -1, boardSize, -boardSize};
        this.solutionPath = new byte[128]; // Max depth buffer

        if (pdb != null && boardSize == 4 && !useGeneralPath) {
            return searchBitboardPath(root, problem.getGoal());
        } else {
            return searchGeneralPath(root, problem.getGoal());
        }
    }

    // --- Bitboard Mode (4x4, PDB optimized) ---
    private Deque<Node> searchBitboardPath(PuzzleBoard start, core.problem.State goal) {
        int[] tiles = start.getPuzzleBoard();
        int zeroPos = start.getZeroPos();
        long bitBoard = 0;
        int idx1 = 0, idx2 = 0, idx3 = 0;

        int[] mapSubset = pdb.getMapSubset();
        int[] mapShift = pdb.getMapShift();

        for (int i = 0; i < 16; i++) {
            bitBoard |= ((long) tiles[i]) << (i << 2);
            if (tiles[i] != 0 && mapSubset[tiles[i]] != -1) {
                int s = mapSubset[tiles[i]], shift = mapShift[tiles[i]];
                if (s == 0) idx1 |= i << shift;
                else if (s == 1) idx2 |= i << shift;
                else idx3 |= i << shift;
            }
        }

        int bound = (pdb.getDb1()[idx1] & 0xFF) + (pdb.getDb2()[idx2] & 0xFF) + (pdb.getDb3()[idx3] & 0xFF);
        while (!found && bound < 100) {
            bound = dfsBitboard(0, bound, zeroPos, -1, idx1, idx2, idx3, bitBoard, mapSubset, mapShift);
        }
        return found ? reconstructPath(start) : null;
    }

    private int dfsBitboard(int g, int bound, int zeroPos, int lastMove,
                            int i1, int i2, int i3, long board, int[] mSub, int[] mSh) {
        if (Thread.currentThread().isInterrupted()) return Integer.MAX_VALUE;
        nodesExpanded++;
        int minExceed = Integer.MAX_VALUE;

        for (int dir = 0; dir < 4; dir++) {
            if (lastMove != -1 && (lastMove ^ 1) == dir) continue;
            if (dir == 0 && (zeroPos & 3) == 3) continue;
            if (dir == 1 && (zeroPos & 3) == 0) continue;
            if (dir == 2 && zeroPos > 11) continue;
            if (dir == 3 && zeroPos < 4) continue;

            int nextZero = zeroPos + moveOffsets[dir];
            int movedTile = (int) ((board >>> (nextZero << 2)) & 0xF);

            int ni1 = i1, ni2 = i2, ni3 = i3;
            int s = mSub[movedTile], sh = mSh[movedTile], diff = (nextZero << sh) ^ (zeroPos << sh);
            if (s == 0) ni1 ^= diff; else if (s == 1) ni2 ^= diff; else ni3 ^= diff;

            int h = (pdb.getDb1()[ni1] & 0xFF) + (pdb.getDb2()[ni2] & 0xFF) + (pdb.getDb3()[ni3] & 0xFF);
            int f = g + 1 + h;
            if (f > bound) { minExceed = Math.min(minExceed, f); continue; }
            if (h == 0) { found = true; solutionPath[g] = (byte) dir; solutionLength = g + 1; return -1; }

            solutionPath[g] = (byte) dir;
            nodesGenerated++;
            int res = dfsBitboard(g + 1, bound, nextZero, dir, ni1, ni2, ni3,
                    (board & ~(0xFL << (nextZero << 2))) | ((long) movedTile << (zeroPos << 2)), mSub, mSh);
            if (found) return -1;
            minExceed = Math.min(minExceed, res);
        }
        return minExceed;
    }

    // --- General Mode (OO-based) ---
    private Deque<Node> searchGeneralPath(PuzzleBoard start, core.problem.State goal) {
        int bound = predictor.heuristics(start, goal);
        while (!found && bound < 100) {
            bound = dfsGeneral(0, bound, start, -1, goal);
        }
        return found ? reconstructPath(start) : null;
    }

    private int dfsGeneral(int g, int bound, PuzzleBoard board, int lastMove, core.problem.State goal) {
        if (Thread.currentThread().isInterrupted()) return Integer.MAX_VALUE;
        nodesExpanded++;
        int minExceed = Integer.MAX_VALUE;

        int zeroPos = board.getZeroPos();
        for (int dir = 0; dir < 4; dir++) {
            if (lastMove != -1 && (lastMove ^ 1) == dir) continue;
            if (dir == 0 && (zeroPos % boardSize) == boardSize - 1) continue;
            if (dir == 1 && (zeroPos % boardSize) == 0) continue;
            if (dir == 2 && zeroPos >= boardSize * (boardSize - 1)) continue;
            if (dir == 3 && zeroPos < boardSize) continue;

            int nextZero = zeroPos + moveOffsets[dir];
            int[] nextTiles = board.getPuzzleBoard();
            int tmp = nextTiles[zeroPos]; nextTiles[zeroPos] = nextTiles[nextZero]; nextTiles[nextZero] = tmp;

            PuzzleBoard nextBoard = new PuzzleBoard(boardSize, nextTiles);
            int h = predictor.heuristics(nextBoard, goal);
            int f = g + 1 + h;

            if (f > bound) { minExceed = Math.min(minExceed, f); continue; }
            if (h == 0) { found = true; solutionPath[g] = (byte) dir; solutionLength = g + 1; return -1; }

            solutionPath[g] = (byte) dir;
            nodesGenerated++;
            int res = dfsGeneral(g + 1, bound, nextBoard, dir, goal);
            if (found) return -1;
            minExceed = Math.min(minExceed, res);
        }
        return minExceed;
    }

    /**
     * Reconstructs the search path from the internal solution byte buffer.
     */
    private Deque<Node> reconstructPath(PuzzleBoard startState) {
        // Create the goal node from the start state
        Node pathNode = new Node(startState, null, null, 0, 0);

        int[] currentTiles = startState.getPuzzleBoard();
        int zeroPos = startState.getZeroPos();

        // Backtrack path using the pre-stored solution direction buffer
        for (int i = 0; i < solutionLength; i++) {
            int dir = solutionPath[i];
            int nextZeroPos = zeroPos + moveOffsets[dir];

            // Swap tiles
            int temp = currentTiles[zeroPos];
            currentTiles[zeroPos] = currentTiles[nextZeroPos];
            currentTiles[nextZeroPos] = temp;

            zeroPos = nextZeroPos;

            // Create next node in the path (using pathCost = i + 1)
            pathNode = new Node(new PuzzleBoard(boardSize, currentTiles.clone()),
                    pathNode, ACTIONS[dir], i + 1, 0);
        }
        return generatePath(pathNode);
    }
}