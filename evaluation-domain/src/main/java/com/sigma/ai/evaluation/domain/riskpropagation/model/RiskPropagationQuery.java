package com.sigma.ai.evaluation.domain.riskpropagation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险传播查询（领域层）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskPropagationQuery {

    private String repoId;

    @Builder.Default
    private List<CodeRiskNodeRef> riskSeeds = new ArrayList<>();

    /**
     * 最大传播深度；未传或非法时由 {@link com.sigma.ai.evaluation.domain.riskpropagation.RiskPropagationDepthPolicy} 解析为有效值。
     */
    private Integer propagationMaxDepth;

    /** 可选：节点预算，未传时使用实现默认值 */
    private Integer maxNodes;

    /** 可选：边预算，未传时使用实现默认值 */
    private Integer maxEdges;
}
