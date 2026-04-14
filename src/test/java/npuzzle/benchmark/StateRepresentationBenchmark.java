package npuzzle.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark comparing state transition models.
 * Evaluates Object-Oriented representations (array cloning) versus
 * System-Level Bitboard structures (64-bit primitive operations).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class StateRepresentationBenchmark {

    // --- Object-Oriented State Data ---
    private int[] initialArray;
    private int zeroPosArray;
    private int newZeroPosArray;

    // --- Bitboard State Data ---
    private long initialBitboard;
    private int zeroPosBit;
    private int newZeroPosBit;

    @Setup
    public void setup() {
        // Initialize a 15-Puzzle board
        initialArray = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0, 15};
        zeroPosArray = 14;
        newZeroPosArray = 10;

        // Compute corresponding 64-bit representation
        initialBitboard = 0L;
        for (int pos = 0; pos < 16; pos++) {
            initialBitboard |= ((long) initialArray[pos]) << (pos << 2);
        }
        zeroPosBit = 14;
        newZeroPosBit = 10;
    }

    /**
     * Test 1: Standard OOP Transition
     * Involves memory allocation (clone) and array assignment.
     */
    @Benchmark
    public void testTraditionalArray(Blackhole blackhole) {
        int[] newState = initialArray.clone();
        newState[zeroPosArray] = newState[newZeroPosArray];
        newState[newZeroPosArray] = 0;
        blackhole.consume(newState);
    }

    /**
     * Test 2: Bitboard Transition
     * Zero-allocation, utilizing bitwise masks and shifts exclusively.
     */
    @Benchmark
    public long testBitboard(Blackhole blackhole) {
        int tileMoved = (int) ((initialBitboard >>> (newZeroPosBit << 2)) & 0xF);

        long nextBoard = initialBitboard & ~(0xFL << (newZeroPosBit << 2));
        nextBoard |= ((long) tileMoved) << (zeroPosBit << 2);

        blackhole.consume(nextBoard);
        return nextBoard;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(StateRepresentationBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}