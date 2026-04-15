package benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark comparing state transition models.
 * Employs a random walk sequence from the goal state to ensure
 * 100% physical reachability and representative spatial distribution.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class StateRepresentationBenchmark {

    private static final int POOL_SIZE = 1024;
    private static final int MASK = POOL_SIZE - 1;

    private int[][] arrayPool;
    private int[] zeroPosPool;
    private int[] nextZeroPosPool;
    private long[] bitboardPool;

    private int index = 0;

    @Setup(Level.Trial)
    public void setup() {
        arrayPool = new int[POOL_SIZE][16];
        zeroPosPool = new int[POOL_SIZE];
        nextZeroPosPool = new int[POOL_SIZE];
        bitboardPool = new long[POOL_SIZE];

        Random rand = new Random(42);
        int[] offsets = {-4, 4, -1, 1};

        int[] currentState = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        int zeroPos = 15;

        for (int i = 0; i < POOL_SIZE; i++) {
            // Walk 5 steps to scramble
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

            arrayPool[i] = currentState.clone();
            zeroPosPool[i] = zeroPos;

            // Determine the next valid move for the benchmark
            int nextTarget;
            while (true) {
                int dir = rand.nextInt(4);
                if (dir == 0 && zeroPos < 4) continue;
                if (dir == 1 && zeroPos > 11) continue;
                if (dir == 2 && (zeroPos & 3) == 0) continue;
                if (dir == 3 && (zeroPos & 3) == 3) continue;
                nextTarget = zeroPos + offsets[dir];
                break;
            }
            nextZeroPosPool[i] = nextTarget;

            long bb = 0L;
            for (int p = 0; p < 16; p++) {
                bb |= ((long) currentState[p]) << (p << 2);
            }
            bitboardPool[i] = bb;
        }
    }

    @Benchmark
    public void testTraditionalArray(Blackhole blackhole) {
        int idx = (index++) & MASK;
        int[] currentState = arrayPool[idx];
        int zPos = zeroPosPool[idx];
        int nPos = nextZeroPosPool[idx];

        int[] newState = currentState.clone();
        newState[zPos] = newState[nPos];
        newState[nPos] = 0;

        blackhole.consume(newState);
    }

    @Benchmark
    public long testBitboard(Blackhole blackhole) {
        int idx = (index++) & MASK;
        long currentBoard = bitboardPool[idx];
        int zPos = zeroPosPool[idx];
        int nPos = nextZeroPosPool[idx];

        int tileMoved = (int) ((currentBoard >>> (nPos << 2)) & 0xF);
        long nextBoard = currentBoard & ~(0xFL << (nPos << 2));
        nextBoard |= ((long) tileMoved) << (zPos << 2);

        blackhole.consume(nextBoard);
        return nextBoard;
    }
}