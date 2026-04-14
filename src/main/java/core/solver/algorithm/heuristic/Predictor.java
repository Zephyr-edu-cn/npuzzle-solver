package core.solver.algorithm.heuristic;

import core.problem.State;

/**
 *  预测器接口
 *  根据当前状态和目标状态，给出当前状态到目标状态的耗散值的一个估计
 *
 */
public interface Predictor {
    /**
     * 根据目标状态，对当前状态进行启发式估值
     * @param
     *      state 被评估的状态
     *      goal  目标状态
     * @return 该状态到目标状态的启发值
     */
    int heuristics(State state, State goal);

    /**
     * 获取启发式名称（用于调试和性能分析）
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 是否支持状态编码（模式数据库需要）
     */
    default boolean supportsEncoding() {
        return false;
    }

    /**
     * 获取状态编码（用于模式数据库查找）
     */
    default long encodeState(State state, State goal) {
        return -1;
    }
}
