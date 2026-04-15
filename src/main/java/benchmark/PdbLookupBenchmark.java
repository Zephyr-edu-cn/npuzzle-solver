package benchmark;

import npuzzle.solver.database.DisjointPatternDatabase;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark evaluating PDB heuristic lookup latency and throughput.
 * Methodological safeguards:
 * 1. Loads actual production PDB data rather than mock data.
 * 2. Generates state pool via Random Walk from goal state to preserve spatial locality.
 * 3. Evaluates both Throughput and Average Time (Latency).
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PdbLookupBenchmark {

    private static final int POOL_SIZE = 1024;
    private static final int MASK = POOL_SIZE - 1;

    private int[][] statePool;
    private int index = 0;

    private Map<Long, Byte> hashMapDb1, hashMapDb2, hashMapDb3;
    private byte[] rawDb1, rawDb2, rawDb3;
    private int[] mapSubset, mapShift;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        statePool = new int[POOL_SIZE][16];
        Random rand = new Random(42);

        // Random walk to preserve realistic spatial locality
        int[] currentState = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        int zeroPos = 15;
        int[] offsets = {-4, 4, -1, 1};

        for (int i = 0; i < POOL_SIZE; i++) {
            statePool[i] = currentState.clone();

            // Walk 5 steps to generate next state
            for(int step=0; step<5; step++) {
                int nextPos;
                while (true) {
                    int dir = rand.nextInt(4);
                    if (dir == 0 && zeroPos < 4) continue;
                    if (dir == 1 && zeroPos > 11) continue;
                    if (dir == 2 && (zeroPos & 3) == 0) continue;
                    if (dir == 3 && (zeroPos & 3) == 3) continue;
                    nextPos = zeroPos + offsets[dir];
                    break;
                }
                currentState[zeroPos] = currentState[nextPos];
                currentState[nextPos] = 0;
                zeroPos = nextPos;
            }
        }

        // Load Real PDB
        DisjointPatternDatabase pdb = DisjointPatternDatabase.loadCompactDatabases();
        rawDb1 = pdb.getDb1();
        rawDb2 = pdb.getDb2();
        rawDb3 = pdb.getDb3();
        mapSubset = pdb.getMapSubset();
        mapShift = pdb.getMapShift();

        // Construct equivalent HashMap for baseline
        hashMapDb1 = new HashMap<>(rawDb1.length / 4);
        hashMapDb2 = new HashMap<>(rawDb2.length / 4);
        hashMapDb3 = new HashMap<>(rawDb3.length / 4);

        for (int i = 0; i < POOL_SIZE; i++) {
            long idx1 = 0, idx2 = 0, idx3 = 0;
            for (int pos = 0; pos < 16; pos++) {
                int tile = statePool[i][pos];
                if (tile == 0) continue;
                int subset = mapSubset[tile];
                if (subset == 0) idx1 |= (long) pos << mapShift[tile];
                else if (subset == 1) idx2 |= (long) pos << mapShift[tile];
                else idx3 |= (long) pos << mapShift[tile];
            }
            // Populate map with realistic keys
            hashMapDb1.put(idx1, rawDb1[(int)idx1]);
            hashMapDb2.put(idx2, rawDb2[(int)idx2]);
            hashMapDb3.put(idx3, rawDb3[(int)idx3]);
        }
    }

    @Benchmark
    public int testTraditionalHashMap(Blackhole blackhole) {
        int[] tiles = statePool[(index++) & MASK];
        long idx1 = 0, idx2 = 0, idx3 = 0;

        for (int pos = 0; pos < 16; pos++) {
            int tile = tiles[pos];
            if (tile == 0) continue;
            int subset = mapSubset[tile];
            if (subset == 0) idx1 |= (long) pos << mapShift[tile];
            else if (subset == 1) idx2 |= (long) pos << mapShift[tile];
            else idx3 |= (long) pos << mapShift[tile];
        }

        int h1 = hashMapDb1.getOrDefault(idx1, (byte) 0);
        int h2 = hashMapDb2.getOrDefault(idx2, (byte) 0);
        int h3 = hashMapDb3.getOrDefault(idx3, (byte) 0);

        int totalH = h1 + h2 + h3;
        blackhole.consume(totalH);
        return totalH;
    }

    @Benchmark
    public int test1DRawByteArray(Blackhole blackhole) {
        int[] tiles = statePool[(index++) & MASK];
        int idx1 = 0, idx2 = 0, idx3 = 0;

        for (int pos = 0; pos < 16; pos++) {
            int tile = tiles[pos];
            if (tile == 0) continue;
            int subset = mapSubset[tile];
            if (subset == 0) idx1 |= pos << mapShift[tile];
            else if (subset == 1) idx2 |= pos << mapShift[tile];
            else idx3 |= pos << mapShift[tile];
        }

        int totalH = (rawDb1[idx1] & 0xFF) + (rawDb2[idx2] & 0xFF) + (rawDb3[idx3] & 0xFF);
        blackhole.consume(totalH);
        return totalH;
    }
}