package com.sigma.ai.evaluation.domain.embedding.adapter;

import com.sigma.ai.evaluation.domain.embedding.model.EmbeddingSearchHit;
import com.sigma.ai.evaluation.domain.embedding.model.EmbeddingSearchQuery;

import java.util.List;

/**
 * 向量数据库访问 Adapter，封装 Milvus 的读写操作。
 * 由 evaluation-infrastructure 模块实现。
 */
public interface EmbeddingStoreAdapter {

    /**
     * 插入或更新节点的嵌入向量。
     *
     * @param nodeId        Neo4j 节点唯一 id
     * @param nodeType      节点类型标识：FILE / TYPE / METHOD
     * @param qualifiedName 供回显的全限定名
     * @param repoId        所属仓库 ID（过滤条件）
     * @param text          待嵌入的原始文本（方法签名+Javadoc 等）
     */
    void upsertEmbedding(String nodeId, String nodeType, String qualifiedName,
                         String repoId, String text);

    /**
     * 批量插入或更新嵌入向量，入参与 {@link #upsertEmbedding} 相同字段。
     *
     * @param nodeIds        节点唯一 id 列表
     * @param nodeTypes      对应的节点类型列表
     * @param qualifiedNames 对应的全限定名列表
     * @param repoId         所属仓库 ID
     * @param texts          待嵌入文本列表（与 nodeIds 一一对应）
     */
    void batchUpsertEmbeddings(List<String> nodeIds, List<String> nodeTypes,
                               List<String> qualifiedNames, String repoId,
                               List<String> texts);

    /**
     * 删除指定节点的嵌入向量。
     *
     * @param nodeId Neo4j 节点唯一 id
     */
    void deleteEmbedding(String nodeId);

    /**
     * 语义检索：返回与查询文本最相似的 topK 个节点 id。
     *
     * @param queryText 查询文本
     * @param repoId    仓库 ID 过滤（null 表示全库检索）
     * @param topK      返回数量
     * @return 相似节点 id 列表
     */
    List<String> semanticSearch(String queryText, String repoId, int topK);

    /**
     * 语义检索（富结果）：含分数、节点类型、全限定名；支持仓库与 node_type 过滤及最低分阈值。
     *
     * @param query 检索参数
     * @return 命中列表（可能少于 topK）
     */
    List<EmbeddingSearchHit> semanticSearchRich(EmbeddingSearchQuery query);

    /**
     * 按 node_id 批量删除向量（与 {@link #upsertEmbedding} 主键规则一致）。
     *
     * @param nodeIds 节点 id 列表，空列表则忽略
     */
    void deleteEmbeddingsByNodeIds(List<String> nodeIds);
}
