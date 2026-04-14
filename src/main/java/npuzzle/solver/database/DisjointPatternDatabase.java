package npuzzle.solver.database;
import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import npuzzle.model.PuzzleBoard;

import java.io.*;
import java.util.Arrays;
/**
 不相交模式数据库
 结合了博客中的位运算技巧：一次遍历计算所有 PDB 索引
 */
public class DisjointPatternDatabase implements Predictor {
    // 直接暴露原始 byte 数组以供极速访问
    public byte[] db1;
    public byte[] db2;
    public byte[] db3;
    // 预计算的查找表，用于替代 HashMap 或搜索
    // mapSubset[tile] = 该方块属于第几个数据库 (0, 1, 2)
    private final int[] mapSubset = new int[16];
    // mapShift[tile] = 该方块在索引中的位移量 (0, 4, 8, 12, 16, 20)
    private final int[] mapShift = new int[16];
    public DisjointPatternDatabase() {
        Arrays.fill(mapSubset, -1);
        Arrays.fill(mapShift, -1);
        // 必须与生成器 PatternDatabaseGenerator 中的 COMPACT_SUBSETS 完全一致！
        setupSubset(0, new int[]{1, 5, 6, 9, 10, 13});
        setupSubset(1, new int[]{7, 8, 11, 12, 14, 15});
        setupSubset(2, new int[]{2, 3, 4});
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
        db.db1 = loadRawBytes("pattern_db_15_663_1.dat");
        db.db2 = loadRawBytes("pattern_db_15_663_2.dat");
        db.db3 = loadRawBytes("pattern_db_15_663_3.dat");

        System.out.println("成功加载 6-6-3 极速数据库 (Raw Bytes)");
        return db;
    }

    private static byte[] loadRawBytes(String filename) throws IOException {
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
            dis.readInt(); // size
            int subsetSize = dis.readInt();
            for (int i = 0; i < subsetSize; i++) dis.readInt();
            int stateCount = dis.readInt();

            int dbSize = 1 << (subsetSize * 4);
            byte[] data = new byte[dbSize];
            dis.readFully(data);
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
    // 兼容接口，给普通 IDA* 用
        if (state instanceof PuzzleBoard) {
            return heuristicsRaw(((PuzzleBoard) state).getPuzzleBoard());
        }
        return 0;
    }
    public int[] getMapSubset() {
        return mapSubset;
    }
    public int[] getMapShift() {
        return mapShift;
    }
    @Override public boolean supportsEncoding() { return true; }
    @Override public long encodeState(State state, State goal) { return 0; }
    /**
     * Provides read-only access to the pattern database arrays.
     * Note: Access is restricted to the solver package for performance.
     */
    public byte[] getDb1() { return db1; }
    public byte[] getDb2() { return db2; }
    public byte[] getDb3() { return db3; }
}