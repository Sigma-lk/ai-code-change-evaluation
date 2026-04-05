package com.sigma.ai.evaluation.domain.codegraph.service;

import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;
import com.sigma.ai.evaluation.domain.codegraph.model.RepositoryNode;

import java.util.List;

/**
 * 代码图谱写入服务，封装将 AST 解析结果批量持久化到 Neo4j 的编排逻辑。
 */
public interface CodeGraphService {

    /**
     * 写入或更新仓库根节点。
     *
     * @param node 仓库节点信息
     */
    void writeRepositoryNode(RepositoryNode node);

    /**
     * 将一批 AST 解析结果批量写入 Neo4j。
     * 内部按节点层次顺序写入（Package → JavaFile → Type → Method → Field → Relation），
     * 保证 MERGE 时父节点已存在。
     *
     * @param results 解析结果列表
     */
    void batchWriteParseResults(List<ParseResult> results);

    /**
     * 查询已存储的文件 checksum，用于全量索引幂等判断。
     *
     * @param filePath 文件绝对路径
     * @return 已存储的 checksum，不存在时返回 null
     */
    String getFileChecksum(String filePath);
}
