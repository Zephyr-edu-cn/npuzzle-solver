package core.util;

public final class Stopwatch {
    private final long start;

    public Stopwatch() {
        start = System.nanoTime();
    }

    /**
     * 返回从创建 Stopwatch 对象到现在的耗时，单位为秒。
     */
    public double elapsedTime() {
        return (System.nanoTime() - start) / 1_000_000_000.0;
    }
}