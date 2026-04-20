package com.sigma.ai.evaluation.domain.riskpropagation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险传播查询完整出参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskPropagationResult {

    private String repoId;

    /** 实际使用的传播深度（1～30） */
    private int effectiveDepth;

    /** 与入参种子顺序一一对应 */
    @Builder.Default
    private List<RiskSeedPropagationResult> results = new ArrayList<>();

    private RiskPropagationTruncation truncation;
}
