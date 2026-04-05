package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Neo4j Package 节点，表示 Java 包。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageNode {

    /**
     * 包唯一标识，格式：{repoId}:{qualifiedName}，
     * 避免多仓库之间同名包的冲突。
     */
    private String id;

    /** 包全限定名，如 com.example.service */
    private String qualifiedName;

    /** 包在文件系统中的路径 */
    private String path;

    /** 所属仓库 ID */
    private String repoId;
}
