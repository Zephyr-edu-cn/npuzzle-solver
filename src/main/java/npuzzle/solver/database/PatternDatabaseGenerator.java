package npuzzle.solver.database;

import java.io.*;
import java.util.Arrays;

/**
 * Generator for the 15-Puzzle Disjoint Pattern Database (PDB).
 *
 * Uses reverse 0-1 BFS with compact integer state encoding.
 * Computes exact heuristic costs from the goal state backwards to all reachable configurations
 * of a specific tile subset. Primitive arrays and a primitive deque avoid per-state objects.
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
        if (subsetTiles == null || subsetTiles.length == 0 || subsetTiles.length > 6) {
            throw new IllegalArgumentException("Pattern size must be between 1 and 6.");
        }
        if (databaseFile == null || databaseFile.isBlank()) {
            throw new IllegalArgumentException("Database filename must not be blank.");
        }

        boolean[] seen = new boolean[16];
        for (int tile : subsetTiles) {
            if (tile <= 0 || tile >= 16 || seen[tile]) {
                throw new IllegalArgumentException("Pattern tiles must be unique values from 1 to 15.");
            }
            seen[tile] = true;
        }
        this.subsetTiles = subsetTiles.clone();
        this.databaseFile = databaseFile;
    }

    /**
     * Executes 0-1 BFS to generate the pattern database.
     */
    public void generate() throws IOException {
        System.out.println("\n=== Starting PDB Generation (0-1 BFS) ===");
        System.out.println("Target Subset: " + Arrays.toString(subsetTiles));
        long startTime = System.currentTimeMillis();

        int k = subsetTiles.length;
        // Total possible encodings: 16 possible positions for each of the k tiles -> 16^k
        int dbSize = 1 << (k * 4);
        byte[] costDb = new byte[dbSize];
        Arrays.fill(costDb, (byte) -1);

        // Full abstract state = pattern encoding plus blank position.
        int fullStateCount = dbSize << 4;
        byte[] distance = new byte[fullStateCount];
        Arrays.fill(distance, (byte) -1);
        long[] settled = new long[(fullStateCount + 63) >>> 6];
        PrimitiveIntDeque queue = new PrimitiveIntDeque(2_000_000);

        // 1. Initialize the goal state encoding
        int goalEncoding = 0;
        for (int i = 0; i < k; i++) {
            int goalPos = subsetTiles[i] - 1;
            goalEncoding |= (goalPos << (i * 4));
        }


        // The blank can initially be in any position not occupied by a target tile.
        for (int blankPos = 0; blankPos < 16; blankPos++) {
            if (!isOccupied(goalEncoding, blankPos, k)) {
                int stateIndex = (goalEncoding << 4) | blankPos;
                distance[stateIndex] = 0;
                queue.addLast(stateIndex);
            }
        }

        int stateCount = 0;
        int maxDepth = 0;
        int lastReportedCost = -1;

        // Standard 0-1 BFS over (pattern encoding, blank position).
        while (!queue.isEmpty()) {
            int stateIndex = queue.removeFirst();
            int settledWord = stateIndex >>> 6;
            long settledBit = 1L << (stateIndex & 63);
            if ((settled[settledWord] & settledBit) != 0) continue;
            settled[settledWord] |= settledBit;

            int encoding = stateIndex >>> 4;
            int blankPos = stateIndex & 0xF;
            int currentCost = distance[stateIndex] & 0xFF;
            if (currentCost > lastReportedCost) {
                lastReportedCost = currentCost;
                System.out.printf("Searching Cost Layer %d... Encodings Found: %d\n",
                        currentCost, stateCount);
            }

            if (costDb[encoding] == -1) {
                costDb[encoding] = (byte) currentCost;
                stateCount++;
                maxDepth = Math.max(maxDepth, currentCost);
            }

            for (int dir = 0; dir < 4; dir++) {
                if (dir == 0 && blankPos < 4) continue;
                if (dir == 1 && blankPos > 11) continue;
                if (dir == 2 && (blankPos & 3) == 0) continue;
                if (dir == 3 && (blankPos & 3) == 3) continue;

                int newBlankPos = blankPos + MOVE_OFFSETS[dir];
                int tileIndex = findSubsetTileIndex(encoding, newBlankPos, k);

                int newEncoding = encoding;
                int moveCost = 0;
                if (tileIndex != -1) {
                    newEncoding = (encoding & ~(0xF << (tileIndex * 4)))
                            | (blankPos << (tileIndex * 4));
                    moveCost = 1;
                }

                int newStateIndex = (newEncoding << 4) | newBlankPos;
                int newCost = currentCost + moveCost;
                if (newCost < (distance[newStateIndex] & 0xFF)) {
                    distance[newStateIndex] = (byte) newCost;
                    if (moveCost == 0) queue.addFirst(newStateIndex);
                    else queue.addLast(newStateIndex);
                }
            }
        }
        // Mark unreachable compact encodings with cost 0 as a defensive fallback.
        for (int i = 0; i < costDb.length; i++) {
            if (costDb[i] == -1) costDb[i] = 0;
        }

        saveDatabase(costDb, stateCount);

        long endTime = System.currentTimeMillis();
        System.out.printf("Generation Complete! Configurations Covered: %d, Max Depth: %d, Time: %d ms\n",
                stateCount, maxDepth, (endTime - startTime));
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
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create resources directory: " + dir.getAbsolutePath());
        }

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
     * Unboxed circular deque used by the 0-1 BFS.
     */
    private static class PrimitiveIntDeque {
        private int[] data;
        private int head;
        private int size;

        PrimitiveIntDeque(int initialCapacity) {
            data = new int[initialCapacity];
        }

        void addFirst(int value) {
            ensureCapacity();
            head = (head - 1 + data.length) % data.length;
            data[head] = value;
            size++;
        }

        void addLast(int value) {
            ensureCapacity();
            data[(head + size) % data.length] = value;
            size++;
        }

        int removeFirst() {
            int value = data[head];
            head = (head + 1) % data.length;
            size--;
            return value;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void ensureCapacity() {
            if (size < data.length) return;

            int[] grown = new int[data.length * 2];
            for (int i = 0; i < size; i++) {
                grown[i] = data[(head + i) % data.length];
            }
            data = grown;
            head = 0;
        }
    }

    public static void main(String[] args) throws IOException {
        for (int i = 0; i < COMPACT_SUBSETS.length; i++) {
            PatternDatabaseGenerator generator = new PatternDatabaseGenerator(
                    COMPACT_SUBSETS[i], COMPACT_FILES[i]);
            generator.generate();
        }
    }
}