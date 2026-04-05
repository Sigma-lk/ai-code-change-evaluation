package com.sigma.ai.evaluation.domain.index.service;

/**
 * 全量索引服务，编排 Git 拉取 → 文件扫描 → AST 解析 → Neo4j 批量写入 → Milvus 嵌入的完整流程。
 */
public interface FullIndexService {

    /**
     * 对指定仓库执行全量重建索引。
     * 流程：clone/pull → walkFiles → parseAST → batchWrite(Neo4j) → asyncEmbed(Milvus)
     *
     * @param repoId 仓库 ID（需在 t_repository 中注册）
     */
    void runFullIndex(String repoId);
}
