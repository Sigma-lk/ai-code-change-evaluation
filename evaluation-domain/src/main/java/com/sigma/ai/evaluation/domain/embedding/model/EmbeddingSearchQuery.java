package com.sigma.ai.evaluation.domain.embedding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 语义检索查询参数（富检索）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingSearchQuery {

    private String queryText;

    /** 仓库过滤，null 表示不按仓库过滤 */
    private String repoId;

    private int topK;

    /** 最低分数（IP），低于则丢弃 */
    private float minScore;

    /** 允许的 node_type 标量值（如 METHOD、TYPE），null 或空表示不过滤 */
    private List<String> nodeTypes;
}
