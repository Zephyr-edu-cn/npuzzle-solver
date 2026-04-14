package npuzzle.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark evaluating the throughput of PDB heuristic lookups.
 * Compares the performance of traditional object-oriented representations (HashMap)
 * against highly optimized system-level representations (1D byte array + bitwise operations).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PdbLookupBenchmark {

    private int[] tiles;

    // --- Object-Oriented Configuration (HashMap) ---
    private Map<Long, Byte> hashMapDb1;
    private Map<Long, Byte> hashMapDb2;
    private Map<Long, Byte> hashMapDb3;

    // --- System-Optimized Configuration (1D Primitive Array) ---
    private byte[] rawDb1;
    private byte[] rawDb2;
    private byte[] rawDb3;
    private int[] mapSubset;
    private int[] mapShift;

    @Setup
    public void setup() {
        // Initialize an arbitrary board state
        tiles = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0, 15};

        // Simulate massive PDB memory allocation (16MB per array)
        int dbSize = 1 << 24;
        rawDb1 = new byte[dbSize];
        rawDb2 = new byte[dbSize];
        rawDb3 = new byte[dbSize];

        // Populate mock data for testing
        rawDb1[12345] = 10;
        rawDb2[67890] = 12;
        rawDb3[4095] = 3;

        mapSubset = new int[16];
        mapShift = new int[16];

        // Mock 6-6-3 database mappings
        for (int i = 0; i < 16; i++) {
            mapSubset[i] = i % 3;
            mapShift[i] = (i % 6) * 4;
        }

        // Initialize Map collections for benchmark baseline
        hashMapDb1 = new HashMap<>();
        hashMapDb2 = new HashMap<>();
        hashMapDb3 = new HashMap<>();
        hashMapDb1.put(12345L, (byte) 10);
        hashMapDb2.put(67890L, (byte) 12);
        hashMapDb3.put(4095L, (byte) 3);
    }

    /**
     * Benchmark 1: Traditional HashMap Lookup
     * Evaluates the overhead caused by Long/Byte boxing and hash collisions.
     */
    @Benchmark
    public int testTraditionalHashMap(Blackhole blackhole) {
        long idx1 = 0, idx2 = 0, idx3 = 0;

        for (int pos = 0; pos < 16; pos++) {
            int tile = tiles[pos];
            if (tile == 0) continue;

            int subset = mapSubset[tile];
            if (subset == 0) {
                idx1 |= (long) pos << mapShift[tile];
            } else if (subset == 1) {
                idx2 |= (long) pos << mapShift[tile];
            } else {
                idx3 |= (long) pos << mapShift[tile];
            }
        }

        int h1 = hashMapDb1.getOrDefault(idx1, (byte) 0);
        int h2 = hashMapDb2.getOrDefault(idx2, (byte) 0);
        int h3 = hashMapDb3.getOrDefault(idx3, (byte) 0);

        int totalH = h1 + h2 + h3;
        blackhole.consume(totalH);
        return totalH;
    }

    /**
     * Benchmark 2: System-Optimized Lookup (Bitboard style)
     * Demonstrates O(1) direct memory access using 1D primitive byte arrays.
     */
    @Benchmark
    public int test1DRawByteArray(Blackhole blackhole) {
        int idx1 = 0, idx2 = 0, idx3 = 0;

        for (int pos = 0; pos < 16; pos++) {
            int tile = tiles[pos];
            if (tile == 0) continue;

            int subset = mapSubset[tile];
            if (subset == 0) {
                idx1 |= pos << mapShift[tile];
            } else if (subset == 1) {
                idx2 |= pos << mapShift[tile];
            } else {
                idx3 |= pos << mapShift[tile];
            }
        }

        int totalH = (rawDb1[idx1] & 0xFF) + (rawDb2[idx2] & 0xFF) + (rawDb3[idx3] & 0xFF);
        blackhole.consume(totalH);
        return totalH;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(PdbLookupBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}