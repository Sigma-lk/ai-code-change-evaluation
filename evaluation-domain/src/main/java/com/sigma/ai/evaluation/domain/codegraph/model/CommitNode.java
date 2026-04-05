package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Neo4j Commit 节点，表示一次 Git 提交。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitNode {

    /** 提交哈希（唯一键） */
    private String hash;

    /** 提交信息 */
    private String message;

    /** 提交作者名 */
    private String author;

    /** 提交作者邮箱 */
    private String email;

    /** 提交时间戳（epoch millis） */
    private long timestamp;

    /** 所属分支 */
    private String branch;

    /** 所属仓库 ID */
    private String repoId;
}
