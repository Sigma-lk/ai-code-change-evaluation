package com.sigma.ai.evaluation.domain.riskpropagation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条可展示的风险传播链。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskImpactChain {

    /**
     * 语义分类，例如 {@code CALLS_UPSTREAM}、{@code CALLS_DOWNSTREAM}、{@code FIELD_ACCESS} 等。
     */
    private String chainKind;

    /** 该链在图上的边数 */
    private int hopCount;

    @Builder.Default
    private List<CodeRiskNodeRef> nodes = new ArrayList<>();

    /** 与 nodes 相邻对之间的边类型序列，可选 */
    @Builder.Default
    private List<String> edgeTypes = new ArrayList<>();
}
