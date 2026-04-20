package com.sigma.ai.evaluation.domain.riskpropagation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个种子节点对应的传播结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskSeedPropagationResult {

    private CodeRiskNodeRef seed;

    private boolean matchedInGraph;

    @Builder.Default
    private List<RiskImpactChain> impactChains = new ArrayList<>();
}
