package com.sigma.ai.evaluation.domain.riskpropagation;

/**
 * 风险传播深度：与产品约定一致的全局硬顶与默认值解析。
 *
 * <p>规则：未传参视为满深度 30；已传合法正整数为 {@code min(请求值, 30)}；非法（非正整数等）回退为 30。
 */
public final class RiskPropagationDepthPolicy {

    /** 用户不可突破的全局硬顶 */
    public static final int MAX_PROPAGATION_DEPTH = 30;

    private RiskPropagationDepthPolicy() {
    }

    /**
     * 解析本次请求实际使用的传播深度，返回值落在 {@code 1}～{@link #MAX_PROPAGATION_DEPTH}。
     *
     * @param propagationMaxDepth 请求体中的可选深度，允许为 {@code null}
     * @return 有效深度
     */
    public static int resolveEffectiveDepth(Integer propagationMaxDepth) {
        if (propagationMaxDepth == null) {
            return MAX_PROPAGATION_DEPTH;
        }
        int raw = propagationMaxDepth;
        if (raw <= 0) {
            return MAX_PROPAGATION_DEPTH;
        }
        return Math.min(raw, MAX_PROPAGATION_DEPTH);
    }
}
