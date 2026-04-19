package com.sigma.ai.evaluation.domain.embedding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Milvus 语义检索单条命中（富字段），供 AI 分析上下文与调试接口使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingSearchHit {

    private String nodeId;

    /** 度量类型为 IP 时的相似度分数（越大越相似） */
    private float score;

    private String nodeType;

    /** 类型为 METHOD 时可能与 nodeId 相同，具体取决于入库规则 */
    private String qualifiedName;

    /**
     * 集合中存在短文本列时由基础设施填充；否则为 null。
     */
    private String evidenceSnippet;
}
