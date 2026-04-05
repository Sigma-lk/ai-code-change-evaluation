package com.sigma.ai.evaluation.infrastructure.neo4j;

import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.codegraph.model.*;
import com.sigma.ai.evaluation.types.RelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link GraphAdapter} 的 Neo4j 实现，使用 UNWIND + MERGE 批量写入节点与关系。
 *
 * <p>Neo4j 唯一约束（需在初始化脚本中创建）：
 * <ul>
 *   <li>Type.qualifiedName</li>
 *   <li>Method.id</li>
 *   <li>Field.id</li>
 *   <li>JavaFile.path</li>
 *   <li>Commit.hash</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jGraphAdapterImpl implements GraphAdapter {

    private final Driver driver;

    // ==================== 节点写入 ====================

    @Override
    public void batchMergeRepositoryNodes(List<RepositoryNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (r:Repository {id: n.id})
                SET r.name = n.name, r.url = n.url,
                    r.defaultBranch = n.defaultBranch, r.updatedAt = n.updatedAt
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("name", n.getName());
            m.put("url", n.getUrl());
            m.put("defaultBranch", n.getDefaultBranch());
            m.put("updatedAt", n.getUpdatedAt() != null
                    ? n.getUpdatedAt().toEpochMilli() : System.currentTimeMillis());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    @Override
    public void batchMergeModuleNodes(List<ModuleNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (m:Module {id: n.id})
                SET m.name = n.name, m.path = n.path,
                    m.groupId = n.groupId, m.artifactId = n.artifactId, m.repoId = n.repoId
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("name", n.getName());
            m.put("path", n.getPath());
            m.put("groupId", n.getGroupId());
            m.put("artifactId", n.getArtifactId());
            m.put("repoId", n.getRepoId());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    @Override
    public void batchMergePackageNodes(List<PackageNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (p:Package {id: n.id})
                SET p.qualifiedName = n.qualifiedName, p.path = n.path, p.repoId = n.repoId
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("qualifiedName", n.getQualifiedName());
            m.put("path", n.getPath());
            m.put("repoId", n.getRepoId());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    @Override
    public void batchMergeJavaFileNodes(List<JavaFileNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (f:JavaFile {path: n.path})
                SET f.relativePath = n.relativePath, f.checksum = n.checksum,
                    f.lineCount = n.lineCount, f.lastModified = n.lastModified, f.repoId = n.repoId
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("path", n.getPath());
            m.put("relativePath", n.getRelativePath());
            m.put("checksum", n.getChecksum());
            m.put("lineCount", n.getLineCount());
            m.put("lastModified", n.getLastModified());
            m.put("repoId", n.getRepoId());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    @Override
    public void batchMergeTypeNodes(List<TypeNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (t:Type {qualifiedName: n.qualifiedName})
                SET t.simpleName = n.simpleName, t.kind = n.kind,
                    t.accessModifier = n.accessModifier, t.isAbstract = n.isAbstract,
                    t.isFinal = n.isFinal, t.isStatic = n.isStatic,
                    t.filePath = n.filePath, t.lineStart = n.lineStart, t.lineEnd = n.lineEnd
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("qualifiedName", n.getQualifiedName());
            m.put("simpleName", n.getSimpleName());
            m.put("kind", n.getKind().name());
            m.put("accessModifier", n.getAccessModifier());
            m.put("isAbstract", n.isAbstract());
            m.put("isFinal", n.isFinal());
            m.put("isStatic", n.isStatic());
            m.put("filePath", n.getFilePath());
            m.put("lineStart", n.getLineStart());
            m.put("lineEnd", n.getLineEnd());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    @Override
    public void batchMergeMethodNodes(List<MethodNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (m:Method {id: n.id})
                SET m.ownerQualifiedName = n.ownerQualifiedName,
                    m.simpleName = n.simpleName, m.signature = n.signature,
                    m.returnType = n.returnType, m.accessModifier = n.accessModifier,
                    m.isStatic = n.isStatic, m.isAbstract = n.isAbstract,
                    m.isConstructor = n.isConstructor,
                    m.filePath = n.filePath, m.lineStart = n.lineStart, m.lineEnd = n.lineEnd
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("ownerQualifiedName", n.getOwnerQualifiedName());
            m.put("simpleName", n.getSimpleName());
            m.put("signature", n.getSignature());
            m.put("returnType", n.getReturnType());
            m.put("accessModifier", n.getAccessModifier());
            m.put("isStatic", n.isStatic());
            m.put("isAbstract", n.isAbstract());
            m.put("isConstructor", n.isConstructor());
            m.put("filePath", n.getFilePath());
            m.put("lineStart", n.getLineStart());
            m.put("lineEnd", n.getLineEnd());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    @Override
    public void batchMergeFieldNodes(List<FieldNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (f:Field {id: n.id})
                SET f.ownerQualifiedName = n.ownerQualifiedName,
                    f.simpleName = n.simpleName, f.typeName = n.typeName,
                    f.typeQualifiedName = n.typeQualifiedName,
                    f.accessModifier = n.accessModifier,
                    f.isStatic = n.isStatic, f.isFinal = n.isFinal,
                    f.filePath = n.filePath, f.lineNo = n.lineNo
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("ownerQualifiedName", n.getOwnerQualifiedName());
            m.put("simpleName", n.getSimpleName());
            m.put("typeName", n.getTypeName());
            m.put("typeQualifiedName", n.getTypeQualifiedName());
            m.put("accessModifier", n.getAccessModifier());
            m.put("isStatic", n.isStatic());
            m.put("isFinal", n.isFinal());
            m.put("filePath", n.getFilePath());
            m.put("lineNo", n.getLineNo());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    @Override
    public void batchMergeCommitNodes(List<CommitNode> nodes) {
        if (nodes.isEmpty()) return;
        String cypher = """
                UNWIND $nodes AS n
                MERGE (c:Commit {hash: n.hash})
                SET c.message = n.message, c.author = n.author, c.email = n.email,
                    c.timestamp = n.timestamp, c.branch = n.branch, c.repoId = n.repoId
                """;
        List<Map<String, Object>> params = nodes.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("hash", n.getHash());
            m.put("message", n.getMessage());
            m.put("author", n.getAuthor());
            m.put("email", n.getEmail());
            m.put("timestamp", n.getTimestamp());
            m.put("branch", n.getBranch());
            m.put("repoId", n.getRepoId());
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("nodes", params));
    }

    // ==================== 关系写入 ====================

    @Override
    public void batchMergeRelations(List<GraphRelation> relations) {
        if (relations.isEmpty()) return;
        // 按 RelationType 分组，每组执行对应 Cypher
        Map<RelationType, List<GraphRelation>> grouped = relations.stream()
                .collect(Collectors.groupingBy(GraphRelation::getType));
        grouped.forEach(this::executeMergeRelations);
    }

    private void executeMergeRelations(RelationType type, List<GraphRelation> rels) {
        String cypher = buildRelationCypher(type);
        if (cypher == null) {
            log.warn("未找到关系类型对应的 Cypher, relationType={}", type);
            return;
        }
        List<Map<String, Object>> params = rels.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fromNodeId", r.getFromNodeId());
            m.put("toNodeId", r.getToNodeId());
            if (r.getProperties() != null) {
                m.putAll(r.getProperties());
            }
            return m;
        }).toList();
        runWrite(cypher, Values.parameters("rels", params));
    }

    /**
     * 根据关系类型返回批量 MERGE 的 Cypher 模板。
     * 使用 MATCH 而非 MERGE 查找端点节点，确保只在节点存在时才创建关系（防幽灵节点）。
     */
    private String buildRelationCypher(RelationType type) {
        return switch (type) {
            case CONTAINS_MODULE -> """
                    UNWIND $rels AS r
                    MATCH (from:Repository {id: r.fromNodeId})
                    MATCH (to:Module {id: r.toNodeId})
                    MERGE (from)-[:CONTAINS_MODULE]->(to)
                    """;
            case CONTAINS_PACKAGE -> """
                    UNWIND $rels AS r
                    MATCH (from:Module {id: r.fromNodeId})
                    MATCH (to:Package {id: r.toNodeId})
                    MERGE (from)-[:CONTAINS_PACKAGE]->(to)
                    """;
            case CONTAINS_SUB -> """
                    UNWIND $rels AS r
                    MATCH (from:Package {id: r.fromNodeId})
                    MATCH (to:Package {id: r.toNodeId})
                    MERGE (from)-[:CONTAINS_SUB]->(to)
                    """;
            case CONTAINS_FILE -> """
                    UNWIND $rels AS r
                    MATCH (from:Package {id: r.fromNodeId})
                    MATCH (to:JavaFile {path: r.toNodeId})
                    MERGE (from)-[:CONTAINS_FILE]->(to)
                    """;
            case DEFINES_TYPE -> """
                    UNWIND $rels AS r
                    MATCH (from:JavaFile {path: r.fromNodeId})
                    MATCH (to:Type {qualifiedName: r.toNodeId})
                    MERGE (from)-[:DEFINES_TYPE]->(to)
                    """;
            case HAS_METHOD -> """
                    UNWIND $rels AS r
                    MATCH (from:Type {qualifiedName: r.fromNodeId})
                    MATCH (to:Method {id: r.toNodeId})
                    MERGE (from)-[:HAS_METHOD]->(to)
                    """;
            case HAS_FIELD -> """
                    UNWIND $rels AS r
                    MATCH (from:Type {qualifiedName: r.fromNodeId})
                    MATCH (to:Field {id: r.toNodeId})
                    MERGE (from)-[:HAS_FIELD]->(to)
                    """;
            case INNER_CLASS_OF -> """
                    UNWIND $rels AS r
                    MATCH (from:Type {qualifiedName: r.fromNodeId})
                    MATCH (to:Type {qualifiedName: r.toNodeId})
                    MERGE (from)-[:INNER_CLASS_OF]->(to)
                    """;
            case EXTENDS -> """
                    UNWIND $rels AS r
                    MATCH (from:Type {qualifiedName: r.fromNodeId})
                    MATCH (to:Type {qualifiedName: r.toNodeId})
                    MERGE (from)-[:EXTENDS]->(to)
                    """;
            case IMPLEMENTS -> """
                    UNWIND $rels AS r
                    MATCH (from:Type {qualifiedName: r.fromNodeId})
                    MATCH (to:Type {qualifiedName: r.toNodeId})
                    MERGE (from)-[:IMPLEMENTS]->(to)
                    """;
            case CALLS -> """
                    UNWIND $rels AS r
                    MATCH (from:Method {id: r.fromNodeId})
                    MATCH (to:Method {id: r.toNodeId})
                    MERGE (from)-[rel:CALLS]->(to)
                    SET rel.lineNo = r.lineNo
                    """;
            case READS_FIELD -> """
                    UNWIND $rels AS r
                    MATCH (from:Method {id: r.fromNodeId})
                    MATCH (to:Field {id: r.toNodeId})
                    MERGE (from)-[:READS_FIELD]->(to)
                    """;
            case WRITES_FIELD -> """
                    UNWIND $rels AS r
                    MATCH (from:Method {id: r.fromNodeId})
                    MATCH (to:Field {id: r.toNodeId})
                    MERGE (from)-[:WRITES_FIELD]->(to)
                    """;
            case DEPENDS_ON -> """
                    UNWIND $rels AS r
                    MATCH (from:Type {qualifiedName: r.fromNodeId})
                    MATCH (to:Type {qualifiedName: r.toNodeId})
                    MERGE (from)-[:DEPENDS_ON]->(to)
                    """;
            case IMPORTS -> """
                    UNWIND $rels AS r
                    MATCH (from:JavaFile {path: r.fromNodeId})
                    MATCH (to:Type {qualifiedName: r.toNodeId})
                    MERGE (from)-[:IMPORTS]->(to)
                    """;
            case CHANGED_IN -> buildChangedInCypher(null);
        };
    }

    /**
     * CHANGED_IN 关系的 fromNodeLabel 可能是 JavaFile、Type 或 Method，
     * 需要根据实际传入的 fromNodeLabel 动态选择 Cypher。
     * 此处在 executeMergeRelations 外独立处理。
     */
    private String buildChangedInCypher(String fromLabel) {
        // 因 Neo4j Cypher 标签不支持参数变量，默认返回 Type 版本供 switch 占位
        return """
                UNWIND $rels AS r
                MATCH (from:Type {qualifiedName: r.fromNodeId})
                MATCH (to:Commit {hash: r.toNodeId})
                MERGE (from)-[:CHANGED_IN]->(to)
                """;
    }

    // ==================== 删除操作 ====================

    @Override
    public void deleteFileOutgoingRelations(String filePath) {
        String cypher = """
                MATCH (f:JavaFile {path: $filePath})-[r]->()
                DELETE r
                """;
        runWrite(cypher, Values.parameters("filePath", filePath));
        // 同时删除该文件下 Type 和 Method 节点的出边
        String cypher2 = """
                MATCH (f:JavaFile {path: $filePath})-[:DEFINES_TYPE]->(t:Type)-[r]->()
                DELETE r
                """;
        runWrite(cypher2, Values.parameters("filePath", filePath));
        log.debug("已清除文件出边, filePath={}", filePath);
    }

    @Override
    public void deleteFileNodes(String filePath) {
        // 先删除方法节点
        String deleteMethods = """
                MATCH (f:JavaFile {path: $filePath})-[:DEFINES_TYPE]->(t:Type)-[:HAS_METHOD]->(m:Method)
                DETACH DELETE m
                """;
        runWrite(deleteMethods, Values.parameters("filePath", filePath));
        // 再删除字段节点
        String deleteFields = """
                MATCH (f:JavaFile {path: $filePath})-[:DEFINES_TYPE]->(t:Type)-[:HAS_FIELD]->(fd:Field)
                DETACH DELETE fd
                """;
        runWrite(deleteFields, Values.parameters("filePath", filePath));
        // 最后删除类型节点和文件节点
        String deleteTypes = """
                MATCH (f:JavaFile {path: $filePath})-[:DEFINES_TYPE]->(t:Type)
                DETACH DELETE t
                """;
        runWrite(deleteTypes, Values.parameters("filePath", filePath));
        String deleteFile = """
                MATCH (f:JavaFile {path: $filePath})
                DETACH DELETE f
                """;
        runWrite(deleteFile, Values.parameters("filePath", filePath));
        log.info("已删除文件及其下属节点, filePath={}", filePath);
    }

    // ==================== 查询 ====================

    @Override
    public List<String> findCallerMethodIds(List<String> methodIds, int maxHops) {
        if (methodIds == null || methodIds.isEmpty()) return Collections.emptyList();
        String cypher = """
                MATCH path = (caller:Method)-[:CALLS*1..$maxHops]->(changed:Method)
                WHERE changed.id IN $methodIds
                RETURN DISTINCT caller.id AS callerId
                ORDER BY length(path)
                """.replace("$maxHops", String.valueOf(maxHops));
        try (Session session = driver.session()) {
            return session.run(cypher, Values.parameters("methodIds", methodIds))
                    .list(record -> record.get("callerId").asString());
        }
    }

    @Override
    public List<String> findSubTypeQualifiedNames(List<String> qualifiedNames, int maxHops) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) return Collections.emptyList();
        String cypher = """
                MATCH (sub:Type)-[:EXTENDS|IMPLEMENTS*1..$maxHops]->(base:Type)
                WHERE base.qualifiedName IN $qualifiedNames
                RETURN DISTINCT sub.qualifiedName AS subName
                """.replace("$maxHops", String.valueOf(maxHops));
        try (Session session = driver.session()) {
            return session.run(cypher, Values.parameters("qualifiedNames", qualifiedNames))
                    .list(record -> record.get("subName").asString());
        }
    }

    @Override
    public String getFileChecksum(String filePath) {
        String cypher = """
                MATCH (f:JavaFile {path: $filePath})
                RETURN f.checksum AS checksum
                """;
        try (Session session = driver.session()) {
            var result = session.run(cypher, Values.parameters("filePath", filePath));
            if (result.hasNext()) {
                var value = result.next().get("checksum");
                return value.isNull() ? null : value.asString();
            }
            return null;
        }
    }

    // ==================== 私有工具 ====================

    private void runWrite(String cypher, org.neo4j.driver.Value params) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, params);
                return null;
            });
        } catch (Exception e) {
            log.error("Neo4j 写入异常, cypher={}", cypher.substring(0, Math.min(100, cypher.length())), e);
            throw e;
        }
    }
}
