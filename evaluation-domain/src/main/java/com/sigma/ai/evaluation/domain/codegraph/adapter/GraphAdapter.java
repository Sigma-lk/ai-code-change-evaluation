package com.sigma.ai.evaluation.domain.codegraph.adapter;

import com.sigma.ai.evaluation.domain.codegraph.model.*;

import java.util.List;

/**
 * 图数据库访问 Adapter，封装对 Neo4j 的所有读写操作。
 * 由 evaluation-infrastructure 模块实现。
 */
public interface GraphAdapter {

    // ==================== 节点批量写入 ====================

    /** 批量 MERGE Repository 节点 */
    void batchMergeRepositoryNodes(List<RepositoryNode> nodes);

    /** 批量 MERGE Module 节点 */
    void batchMergeModuleNodes(List<ModuleNode> nodes);

    /** 批量 MERGE Package 节点 */
    void batchMergePackageNodes(List<PackageNode> nodes);

    /** 批量 MERGE JavaFile 节点 */
    void batchMergeJavaFileNodes(List<JavaFileNode> nodes);

    /** 批量 MERGE Type 节点 */
    void batchMergeTypeNodes(List<TypeNode> nodes);

    /** 批量 MERGE Method 节点 */
    void batchMergeMethodNodes(List<MethodNode> nodes);

    /** 批量 MERGE Field 节点 */
    void batchMergeFieldNodes(List<FieldNode> nodes);

    /** 批量 MERGE Commit 节点 */
    void batchMergeCommitNodes(List<CommitNode> nodes);

    // ==================== 关系批量写入 ====================

    /**
     * 批量 MERGE 关系。
     * 内部按 RelationType 分组并执行对应 Cypher。
     *
     * @param relations 待写入的关系列表
     */
    void batchMergeRelations(List<GraphRelation> relations);

    // ==================== 删除操作（增量更新使用） ====================

    /**
     * 删除指定文件节点下所有出边（关系），保留节点本身。
     * MODIFIED 文件更新前先清除旧关系，避免脏数据残留。
     *
     * @param filePath 文件绝对路径
     */
    void deleteFileOutgoingRelations(String filePath);

    /**
     * DETACH DELETE 指定文件及其下属 Type/Method/Field 节点和所有关联边。
     * DELETED 文件使用。
     *
     * @param filePath 文件绝对路径
     */
    void deleteFileNodes(String filePath);

    // ==================== 查询（影响面分析） ====================

    /**
     * 向上查找调用者：给定变更方法 ID 集合，返回在 maxHops 跳内调用它们的所有方法 ID。
     *
     * @param methodIds 变更方法 id 列表
     * @param maxHops   最大跳数
     * @return 调用者方法 id 列表（含距离信息用 Cypher 返回时再封装）
     */
    List<String> findCallerMethodIds(List<String> methodIds, int maxHops);

    /**
     * 向下查找子类型：给定类型全限定名集合，返回在 maxHops 跳内继承/实现它们的所有子类型全限定名。
     *
     * @param qualifiedNames 类型全限定名列表
     * @param maxHops        最大跳数
     * @return 子类型全限定名列表
     */
    List<String> findSubTypeQualifiedNames(List<String> qualifiedNames, int maxHops);

    /**
     * 根据文件绝对路径查询已存储的 checksum，用于全量索引幂等判断。
     *
     * @param filePath 文件绝对路径
     * @return 已存储的 checksum，不存在时返回 null
     */
    String getFileChecksum(String filePath);
}
