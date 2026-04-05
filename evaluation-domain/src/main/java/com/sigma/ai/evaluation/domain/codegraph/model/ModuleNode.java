package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Neo4j Module 节点，表示 Maven 子模块。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleNode {

    /** 模块唯一标识，格式：{repoId}:{artifactId} */
    private String id;

    /** 模块名称（artifactId） */
    private String name;

    /** 模块在仓库中的相对路径 */
    private String path;

    /** Maven groupId */
    private String groupId;

    /** Maven artifactId */
    private String artifactId;

    /** 所属仓库 ID */
    private String repoId;
}
