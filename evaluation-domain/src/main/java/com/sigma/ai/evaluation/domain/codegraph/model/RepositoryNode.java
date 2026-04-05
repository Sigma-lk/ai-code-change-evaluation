package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Neo4j Repository 节点，表示一个 Git 代码仓库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryNode {

    /** 仓库唯一标识（业务键） */
    private String id;

    /** 仓库名称 */
    private String name;

    /** 克隆 URL */
    private String url;

    /** 默认分支 */
    private String defaultBranch;

    /** 最后更新时间 */
    private Instant updatedAt;
}
