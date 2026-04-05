package com.sigma.ai.evaluation.domain.embedding.service;

import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;

import java.util.List;

/**
 * 向量嵌入服务，负责将代码图谱节点异步向量化并写入向量数据库。
 */
public interface EmbeddingService {

    /**
     * 异步为一批 ParseResult 中的 Type 和 Method 节点生成并写入嵌入向量。
     *
     * @param results 解析结果列表
     * @param repoId  所属仓库 ID
     */
    void submitAsync(List<ParseResult> results, String repoId);
}
