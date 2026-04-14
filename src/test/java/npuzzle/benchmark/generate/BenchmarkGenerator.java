package npuzzle.benchmark.generate;

import java.io.File;
import java.io.PrintWriter;
import java.util.Random;

/**
 * 学术级测试用例生成器
 * 通过随机游走（Random Walk）保证生成的 100 个实例 100% 物理有解。
 */
public class BenchmarkGenerator {

    public static void main(String[] args) {
        // 输出文件路径
        String outputFile = "resources/my_benchmark_100.txt";

        // 确保 resources 目录存在
        new File("resources").mkdirs();

        try (PrintWriter writer = new PrintWriter(outputFile, "UTF-8")) {
            Random rand = new Random(42); // 固定随机种子，保证每次生成的数据一致，方便复现

            // 你的引擎的目标状态 (空格在最后)
            int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};

            // 四个移动方向：上、下、左、右对应的 1D 数组索引偏移
            int[] offsets = {-4, 4, -1, 1};

            System.out.println("⚙️ 开始生成 100 个高难度且绝对有解的测试实例...");

            for (int i = 0; i < 100; i++) {
                int[] state = goal.clone();
                int blankPos = 15; // 空格初始在右下角

                // 随机走 80 到 150 步，确保充分打乱，生成高难度状态
                int steps = 80 + rand.nextInt(70);
                int lastBlankPos = -1; // 记录上一步的空格位置，避免来回抖动(无意义移动)

                for (int s = 0; s < steps; s++) {
                    int[] validMoves = new int[4];
                    int validCount = 0;

                    for (int dir = 0; dir < 4; dir++) {
                        int offset = offsets[dir];
                        // 边界检查
                        if (dir == 0 && blankPos < 4) continue;          // 顶端不能向上
                        if (dir == 1 && blankPos > 11) continue;         // 底端不能向下
                        if (dir == 2 && (blankPos % 4) == 0) continue;   // 左端不能向左
                        if (dir == 3 && (blankPos % 4) == 3) continue;   // 右端不能向右

                        int nextBlank = blankPos + offset;
                        if (nextBlank != lastBlankPos) { // 不走回头路
                            validMoves[validCount++] = nextBlank;
                        }
                    }

                    // 随机选一个合法移动
                    int nextBlank = validMoves[rand.nextInt(validCount)];

                    // 交换空格和数字
                    state[blankPos] = state[nextBlank];
                    state[nextBlank] = 0;

                    lastBlankPos = blankPos;
                    blankPos = nextBlank;
                }

                // 按照 Feeder 要求的格式拼接字符串：尺寸 初始状态 目标状态
                StringBuilder sb = new StringBuilder("4 ");
                for (int val : state) sb.append(val).append(" ");
                for (int val : goal) sb.append(val).append(" ");

                writer.println(sb.toString().trim());
            }

            System.out.println("✅ 生成成功！文件保存在: " + outputFile);
            System.out.println("👉 下一步：请将 KorfBatchTester 中的 inputFile 改为这个新文件，然后重新运行！");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}