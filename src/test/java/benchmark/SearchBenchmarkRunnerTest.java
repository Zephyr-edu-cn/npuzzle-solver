package benchmark;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchBenchmarkRunnerTest {

    @Test
    void configOrderIsBalancedOverOneTrialCycle() {
        int configCount = SearchBenchmarkRunner.CONFIGS.length;
        int[][] positionCounts = new int[configCount][configCount];

        for (int trial = 1; trial <= configCount; trial++) {
            String[] order = SearchBenchmarkRunner.configOrder(trial, 1);
            Set<String> unique = new HashSet<>();
            for (int position = 0; position < order.length; position++) {
                unique.add(order[position]);
                for (int config = 0; config < configCount; config++) {
                    if (SearchBenchmarkRunner.CONFIGS[config].equals(order[position])) {
                        positionCounts[config][position]++;
                    }
                }
            }
            assertEquals(configCount, unique.size());
        }

        for (int config = 0; config < configCount; config++) {
            for (int position = 0; position < configCount; position++) {
                assertEquals(1, positionCounts[config][position]);
            }
        }
    }

    @Test
    void effectiveBranchingFactorReconstructsNodeCount() {
        long expandedNodes = 590_000;
        int depth = 45;

        double ebf = SearchBenchmarkRunner.calculateEBF(expandedNodes, depth);
        double reconstructed = (Math.pow(ebf, depth + 1) - 1) / (ebf - 1);

        assertTrue(Math.abs(reconstructed - expandedNodes) / expandedNodes < 0.001);
    }
}
