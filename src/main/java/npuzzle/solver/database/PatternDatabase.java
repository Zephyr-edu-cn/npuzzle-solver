package npuzzle.solver.database;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

import java.io.*;
import java.util.Arrays;

/**
 * Pattern Database implementation using lookup-table optimization.
 * Designed for static pattern retrieval without full disjoint separation.
 */
public class PatternDatabase implements Predictor {
    private final int[] subsetTiles;
    private final byte[] costDb;
    private final int stateCount;

    // O(1) lookup table: tileMap[tile_value] = subset_index
    // Unmapped tiles default to -1
    private final byte[] tileMap;

    public PatternDatabase(String databaseFile) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(databaseFile)))) {
            dis.readInt(); // size
            int subsetSize = dis.readInt();
            this.subsetTiles = new int[subsetSize];
            for (int i = 0; i < subsetSize; i++) {
                this.subsetTiles[i] = dis.readInt();
            }

            this.stateCount = dis.readInt();

            int dbSize = 1 << (subsetTiles.length * 4);
            this.costDb = new byte[dbSize];
            dis.readFully(this.costDb);
        }

        this.tileMap = new byte[16];
        Arrays.fill(this.tileMap, (byte) -1);
        for (int i = 0; i < subsetTiles.length; i++) {
            int tile = subsetTiles[i];
            if (tile >= 0 && tile < 16) {
                this.tileMap[tile] = (byte) i;
            }
        }
    }

    @Override
    public int heuristics(State state, State goal) {
        int encoding = 0;
        int[] tiles = ((PuzzleBoard) state).getPuzzleBoard();

        for (int pos = 0; pos < 16; pos++) {
            int tile = tiles[pos];
            if (tile == 0) continue;

            int subsetIndex = tileMap[tile];
            if (subsetIndex != -1) {
                encoding |= (pos & 0xF) << (subsetIndex * 4);
            }
        }

        if (encoding < 0 || encoding >= costDb.length) return 0;
        int cost = costDb[encoding];
        return (cost == -1) ? 0 : cost;
    }

    public static int encodeStateUnifiedInt(PuzzleBoard board, int[] subsetTiles) {
        int encoding = 0;
        int[] tiles = board.getPuzzleBoard();
        for (int i = 0; i < subsetTiles.length; i++) {
            int tile = subsetTiles[i];
            for (int pos = 0; pos < tiles.length; pos++) {
                if (tiles[pos] == tile) {
                    encoding |= (pos & 0xF) << (i * 4);
                    break;
                }
            }
        }
        return encoding;
    }

    @Override
    public boolean supportsEncoding() { return true; }

    @Override
    public long encodeState(State state, State goal) { return 0; }

    public int size() { return stateCount; }
}