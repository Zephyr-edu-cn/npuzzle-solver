package benchmark.generate;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.searcher.AbstractSearcher;
import npuzzle.solver.database.DisjointPatternDatabase;
import npuzzle.solver.IdAStar;
import java.io.PrintWriter;
import java.util.Random;

public class HardInstanceMiner {
    public static void main(String[] args) throws Exception {
        EngineFeeder feeder = (EngineFeeder) Class.forName("npuzzle.runner.PuzzleFeeder").getDeclaredConstructor().newInstance();
        DisjointPatternDatabase patternDB = DisjointPatternDatabase.loadCompactDatabases();

        System.out.println("⛏️ 开始挖掘极难实例 (目标步数 >= 55) ...");
        PrintWriter writer = new PrintWriter("resources/hard_5_instances.txt", "UTF-8");
        Random rand = new Random(42);
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        int[] offsets = {-4, 4, -1, 1};

        int foundCount = 0;
        int attempts = 0;

        // 手动把你的那个 60 步逆序魔鬼用例作为第一个写入
        writer.println("4 15 14 13 12 11 10 9 8 7 6 5 4 3 1 2 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 0");
        foundCount++;

        while (foundCount < 5) {
            attempts++;
            int[] state = goal.clone();
            int blankPos = 15;
            int lastBlankPos = -1;

            // 深度随机游走 150 步
            for (int s = 0; s < 150; s++) {
                int[] validMoves = new int[4];
                int validCount = 0;
                for (int dir = 0; dir < 4; dir++) {
                    int offset = offsets[dir];
                    if (dir == 0 && blankPos < 4) continue;
                    if (dir == 1 && blankPos > 11) continue;
                    if (dir == 2 && (blankPos % 4) == 0) continue;
                    if (dir == 3 && (blankPos % 4) == 3) continue;
                    int nextBlank = blankPos + offset;
                    if (nextBlank != lastBlankPos) validMoves[validCount++] = nextBlank;
                }
                int nextBlank = validMoves[rand.nextInt(validCount)];
                state[blankPos] = state[nextBlank];
                state[nextBlank] = 0;
                lastBlankPos = blankPos;
                blankPos = nextBlank;
            }

            // 构建问题并用极速引擎试解
            StringBuilder sb = new StringBuilder("4 ");
            for (int val : state) sb.append(val).append(" ");
            for (int val : goal) sb.append(val).append(" ");

            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            lines.add(sb.toString().trim());
            Problem p = feeder.getProblems(lines).get(0);

            core.solver.queue.Frontier frontier = feeder.getFrontier(core.solver.queue.EvaluationType.FULL);
            AbstractSearcher searcher = new IdAStar(frontier, patternDB);
            java.util.Deque<core.solver.queue.Node> path = searcher.search(p);

            if (path != null) {
                int steps = path.size() - 1;
                if (steps >= 55) {  // 🔥 只要步数大于等于 55 的恶魔用例
                    writer.println(sb.toString().trim());
                    foundCount++;
                    System.out.printf("✅ 挖到第 %d 个！步数: %d, 尝试次数: %d\n", foundCount, steps, attempts);
                }
            }
        }
        writer.close();
        System.out.println("🎯 5 个极难用例已保存至 resources/hard_5_instances.txt");
    }
}