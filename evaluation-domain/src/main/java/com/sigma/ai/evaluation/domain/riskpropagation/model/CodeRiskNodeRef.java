package com.sigma.ai.evaluation.domain.riskpropagation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险传播链中的节点引用，与上游 Dify / 变更证据中的 kind + 业务键对齐。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeRiskNodeRef {

    /** {@code TYPE}、{@code METHOD}、{@code FIELD} */
    private String kind;

    /**
     * 与 Neo4j 业务键一致：Type 为 {@code qualifiedName}；Method 为 {@code Method.id}；Field 为 {@code Field.id}。
     */
    private String qualifiedName;

    /** 来自图属性或入参，可为空 */
    private String filePath;
}
