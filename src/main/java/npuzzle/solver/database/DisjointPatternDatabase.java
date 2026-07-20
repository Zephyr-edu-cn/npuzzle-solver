package npuzzle.solver.database;
import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

import java.io.*;
import java.util.Arrays;
/**
 不相交模式数据库
 一次遍历计算所有 PDB 索引
 */
public final class DisjointPatternDatabase implements Predictor {
    private static final int BOARD_SIZE = 4;
    private static final int[][] SUBSETS = {
            {1, 5, 6, 9, 10, 13},
            {7, 8, 11, 12, 14, 15},
            {2, 3, 4}
    };
    private static final PuzzleBoard CANONICAL_GOAL = new PuzzleBoard(BOARD_SIZE,
            new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0});
    private byte[] db1;
    private byte[] db2;
    private byte[] db3;
    // 预计算的查找表，用于替代 HashMap 或搜索
    // mapSubset[tile] = 该方块属于第几个数据库 (0, 1, 2)
    private final int[] mapSubset = new int[16];
    // mapShift[tile] = 该方块在索引中的位移量 (0, 4, 8, 12, 16, 20)
    private final int[] mapShift = new int[16];
    private DisjointPatternDatabase() {
        Arrays.fill(mapSubset, -1);
        Arrays.fill(mapShift, -1);
        // 必须与生成器 PatternDatabaseGenerator 中的 COMPACT_SUBSETS 完全一致！
        setupSubset(0, SUBSETS[0]);
        setupSubset(1, SUBSETS[1]);
        setupSubset(2, SUBSETS[2]);
    }
    private void setupSubset(int subsetId, int[] tiles) {
        for (int i = 0; i < tiles.length; i++) {
            int tile = tiles[i];
            mapSubset[tile] = subsetId;
            mapShift[tile] = i * 4; // 每个位置占4位
        }
    }
    public static DisjointPatternDatabase loadCompactDatabases() throws IOException {
        DisjointPatternDatabase db = new DisjointPatternDatabase();
        // 加载原始 byte 数组
        db.db1 = loadRawBytes("pattern_db_15_663_1.dat", SUBSETS[0]);
        db.db2 = loadRawBytes("pattern_db_15_663_2.dat", SUBSETS[1]);
        db.db3 = loadRawBytes("pattern_db_15_663_3.dat", SUBSETS[2]);

        System.out.println("[INFO] 6-6-3 Compact Pattern Database loaded successfully (Raw Bytes)");
        return db;
    }

    private static byte[] loadRawBytes(String filename, int[] expectedSubset) throws IOException {
        // 【优化】通过当前类的 ClassLoader 加载资源，支持打成 JAR 包后运行
        // 假设 .dat 文件放在了项目的 resources 根目录下
        InputStream is = DisjointPatternDatabase.class.getResourceAsStream("/" + filename);
        if (is == null) {
            // 兼容备用方案：如果在开发环境中还没移动到 resources，尝试直接读取当前目录
            File file = new File(filename);
            if (file.exists()) {
                is = new FileInputStream(file);
            } else {
                throw new FileNotFoundException("Cannot find pattern DB file: " + filename + " in classpath or local directory.");
            }
        }

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(is))) {
            int boardSize = dis.readInt();
            int subsetSize = dis.readInt();
            if (boardSize != BOARD_SIZE || subsetSize != expectedSubset.length) {
                throw new IOException("Pattern DB header mismatch: " + filename);
            }

            int[] subset = new int[subsetSize];
            for (int i = 0; i < subsetSize; i++) subset[i] = dis.readInt();
            int stateCount = dis.readInt();
            if (!Arrays.equals(subset, expectedSubset)) {
                throw new IOException("Pattern DB subset mismatch: " + filename);
            }
            int expectedStateCount = 1;
            for (int i = 0; i < subsetSize; i++) expectedStateCount *= BOARD_SIZE * BOARD_SIZE - i;
            if (stateCount != expectedStateCount) {
                throw new IOException("Pattern DB state count mismatch: " + filename);
            }

            int dbSize = 1 << (subsetSize * 4);
            byte[] data = new byte[dbSize];
            dis.readFully(data);
            if (dis.read() != -1) {
                throw new IOException("Unexpected trailing data in pattern DB: " + filename);
            }
            return data;
        }
    }

    /**
     一次遍历棋盘，同时计算 3 个数据库的索引
     */
    public int heuristicsRaw(int[] tiles) {
        int idx1 = 0;
        int idx2 = 0;
        int idx3 = 0;
        // 倒序遍历或正序遍历皆可，这里扫描 16 个位置
        for (int pos = 0; pos < 16; pos++) {
            int tile = tiles[pos];
            if (tile == 0) continue; // 空格不参与 PDB
            // 查表获取该方块属于哪个库，以及移位多少
            // 相比之前的 map.get()，这里是纯数组访问，极快
            int subset = mapSubset[tile];

            // 利用位运算构建索引：索引 |= 位置 << 移位
            // 注意：Generator 里是 (pos & 0xF) << (i * 4)
            // 这里 mapShift[tile] 就是预存好的 (i * 4)
            if (subset == 0) {
                idx1 |= pos << mapShift[tile];
            } else if (subset == 1) {
                idx2 |= pos << mapShift[tile];
            } else { // subset == 2
                idx3 |= pos << mapShift[tile];
            }
        }
        // 直接查数组求和
        // & 0xFF 是为了把 signed byte 转为 unsigned int (虽然 heuristic 不会超过 127，但保险起见)
        return (db1[idx1] & 0xFF) + (db2[idx2] & 0xFF) + (db3[idx3] & 0xFF);
    }

    @Override
    public int heuristics(State state, State goal) {
        if (!(state instanceof PuzzleBoard)) {
            throw new IllegalArgumentException("Pattern database requires a PuzzleBoard state.");
        }
        if (!supportsGoal(goal)) {
            throw new IllegalArgumentException("The 6-6-3 pattern database supports only the canonical 4x4 goal [1..15, 0].");
        }
        return heuristicsRaw(((PuzzleBoard) state).getPuzzleBoard());
    }
    public boolean supportsGoal(State goal) {
        return CANONICAL_GOAL.equals(goal);
    }

    public int[] getMapSubset() {
        return mapSubset;
    }
    public int[] getMapShift() {
        return mapShift;
    }
    @Override
    public boolean supportsEncoding() { return true; }

    @Override
    public long encodeState(State state, State goal) {
        if (!(state instanceof PuzzleBoard)) {
            throw new IllegalArgumentException("Pattern database requires a PuzzleBoard state.");
        }
        if (!supportsGoal(goal)) {
            throw new IllegalArgumentException("The 6-6-3 pattern database supports only the canonical 4x4 goal [1..15, 0].");
        }

        long encoded = 0;
        int[] tiles = ((PuzzleBoard) state).getPuzzleBoard();
        for (int pos = 0; pos < tiles.length; pos++) {
            int tile = tiles[pos];
            if (tile == 0) continue;
            int subset = mapSubset[tile];
            int shift = mapShift[tile] + subset * 24;
            encoded |= (long) pos << shift;
        }
        return encoded;
    }
    /**
     * Raw hot-path access. Callers must treat the returned arrays as read-only.
     */
    public byte[] getDb1() { return db1; }
    public byte[] getDb2() { return db2; }
    public byte[] getDb3() { return db3; }
}