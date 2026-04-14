package npuzzle.solver.database;

import java.io.*;
import java.util.Arrays;

/**
 * Generator for the 15-Puzzle Disjoint Pattern Database (PDB).
 *
 * Uses a reverse 0-1 Breadth-First Search (BFS) combined with 64-bit state compression.
 * Computes exact heuristic costs from the goal state backwards to all reachable configurations
 * of a specific tile subset. Memory allocations are strictly minimized to handle
 * hundreds of millions of states efficiently.
 */
public class PatternDatabaseGenerator {
    private final int boardSize = 4;

    // The 6-6-3 partitioning scheme
    private static final int[][] COMPACT_SUBSETS = {
            {1, 5, 6, 9, 10, 13},       // Database 1
            {7, 8, 11, 12, 14, 15},     // Database 2
            {2, 3, 4}                   // Database 3
    };

    private static final String[] COMPACT_FILES = {
            "pattern_db_15_663_1.dat",
            "pattern_db_15_663_2.dat",
            "pattern_db_15_663_3.dat"
    };

    // Movement offsets for a 1D array representing a 4x4 grid: UP, DOWN, LEFT, RIGHT
    private static final int[] MOVE_OFFSETS = {-4, 4, -1, 1};

    private final int[] subsetTiles;
    private final String databaseFile;

    public PatternDatabaseGenerator(int[] subsetTiles, String databaseFile) {
        this.subsetTiles = subsetTiles.clone();
        this.databaseFile = databaseFile;
    }

    /**
     * Executes the 0-1 BFS to generate the pattern database.
     */
    public void generate() {
        System.out.println("\n=== Starting PDB Generation (0-1 BFS) ===");
        System.out.println("Target Subset: " + Arrays.toString(subsetTiles));
        long startTime = System.currentTimeMillis();

        int k = subsetTiles.length;
        // Total possible encodings: 16 possible positions for each of the k tiles -> 16^k
        int dbSize = 1 << (k * 4);
        byte[] costDb = new byte[dbSize];
        Arrays.fill(costDb, (byte) -1);

        // Visited array: Requires tracking both the subset encoding and the blank (zero) position.
        // Total states = dbSize * 16. Compressed into a long array (64 bits per element).
        int visitedArraySize = (dbSize * 16) / 64;
        if ((dbSize * 16) % 64 != 0) visitedArraySize++;
        long[] visited = new long[visitedArraySize];

        // Custom primitive deques to bypass object allocation overhead in Java Collections
        PrimitiveLongDeque currentQueue = new PrimitiveLongDeque(2_000_000);
        PrimitiveLongDeque nextQueue = new PrimitiveLongDeque(2_000_000);

        // 1. Initialize the goal state encoding
        int goalEncoding = 0;
        for (int i = 0; i < k; i++) {
            int goalPos = subsetTiles[i] - 1;
            goalEncoding |= (goalPos << (i * 4));
        }

        costDb[goalEncoding] = 0;

        // The blank can initially be in any position not occupied by a target tile
        for (int blankPos = 0; blankPos < 16; blankPos++) {
            if (!isOccupied(goalEncoding, blankPos, k)) {
                int stateIndex = (goalEncoding << 4) | blankPos;
                visited[stateIndex >>> 6] |= (1L << (stateIndex & 63));
                currentQueue.addLast(((long) goalEncoding << 8) | blankPos);
            }
        }

        int currentCost = 0;
        int stateCount = 0;

        // 2. Core 0-1 BFS Loop
        while (!currentQueue.isEmpty() || !nextQueue.isEmpty()) {
            if (currentQueue.isEmpty()) {
                // Swap queues when the current cost level is exhausted
                PrimitiveLongDeque temp = currentQueue;
                currentQueue = nextQueue;
                nextQueue = temp;
                currentCost++;
                System.out.printf("Searching Cost Layer %d... States Found: %d\n", currentCost, stateCount);
            }

            int head = 0;
            while (head < currentQueue.size) {
                long state = currentQueue.data[head++];
                int encoding = (int) (state >>> 8);
                int blankPos = (int) (state & 0xFF);

                // Record the minimum cost encountered
                if (costDb[encoding] == -1) {
                    costDb[encoding] = (byte) currentCost;
                    stateCount++;
                }

                // Expand successor states by moving the blank tile
                for (int dir = 0; dir < 4; dir++) {
                    if (dir == 0 && blankPos < 4) continue;          // UP boundary
                    if (dir == 1 && blankPos > 11) continue;         // DOWN boundary
                    if (dir == 2 && (blankPos & 3) == 0) continue;   // LEFT boundary
                    if (dir == 3 && (blankPos & 3) == 3) continue;   // RIGHT boundary

                    int newBlankPos = blankPos + MOVE_OFFSETS[dir];
                    int tileIndex = findSubsetTileIndex(encoding, newBlankPos, k);

                    int newEncoding;
                    int moveCost;

                    if (tileIndex != -1) {
                        // The blank swaps with a target tile: cost is 1
                        newEncoding = (encoding & ~(0xF << (tileIndex * 4))) | (blankPos << (tileIndex * 4));
                        moveCost = 1;
                    } else {
                        // The blank swaps with a non-target (don't care) tile: cost is 0
                        newEncoding = encoding;
                        moveCost = 0;
                    }

                    int newStateIndex = (newEncoding << 4) | newBlankPos;

                    // Check if state is unvisited
                    if ((visited[newStateIndex >>> 6] & (1L << (newStateIndex & 63))) == 0) {
                        visited[newStateIndex >>> 6] |= (1L << (newStateIndex & 63));
                        long nextState = ((long) newEncoding << 8) | newBlankPos;

                        if (moveCost == 0) {
                            currentQueue.addLast(nextState); // Push to current cost queue
                        } else {
                            nextQueue.addLast(nextState);    // Push to next cost queue
                        }
                    }
                }
            }
            currentQueue.clear();
        }

        // 3. Mark unreachable configurations with cost 0 (fallbacks)
        for (int i = 0; i < costDb.length; i++) {
            if (costDb[i] == -1) costDb[i] = 0;
        }

        try {
            saveDatabase(costDb, stateCount);
        } catch (IOException e) {
            System.err.println("Failed to save database: " + e.getMessage());
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("Generation Complete! Configurations Covered: %d, Max Depth: %d, Time: %d ms\n",
                stateCount, currentCost, (endTime - startTime));
    }

    private boolean isOccupied(int encoding, int targetPos, int k) {
        return findSubsetTileIndex(encoding, targetPos, k) != -1;
    }

    private int findSubsetTileIndex(int encoding, int targetPos, int k) {
        for (int i = 0; i < k; i++) {
            int pos = (encoding >>> (i * 4)) & 0xF;
            if (pos == targetPos) return i;
        }
        return -1;
    }

    private void saveDatabase(byte[] costDb, int stateCount) throws IOException {
        // Creates the resources directory if it doesn't exist
        File dir = new File("src/main/resources");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, databaseFile);
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            dos.writeInt(boardSize);
            dos.writeInt(subsetTiles.length);
            for (int tile : subsetTiles) dos.writeInt(tile);
            dos.writeInt(stateCount);
            dos.write(costDb);
        }
    }

    /**
     * A highly optimized, unboxed dynamic array acting as a double-ended queue.
     * Eliminates java.lang.Long boxing overhead present in standard Collections.
     */
    private static class PrimitiveLongDeque {
        long[] data;
        int size = 0;

        PrimitiveLongDeque(int initialCapacity) {
            data = new long[initialCapacity];
        }

        void addLast(long value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = value;
        }

        boolean isEmpty() { return size == 0; }

        void clear() { size = 0; }
    }

    public static void main(String[] args) {
        for (int i = 0; i < COMPACT_SUBSETS.length; i++) {
            PatternDatabaseGenerator generator = new PatternDatabaseGenerator(
                    COMPACT_SUBSETS[i], COMPACT_FILES[i]);
            generator.generate();
        }
    }
}