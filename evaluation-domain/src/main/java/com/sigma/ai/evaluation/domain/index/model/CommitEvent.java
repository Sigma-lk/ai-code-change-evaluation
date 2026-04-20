package com.sigma.ai.evaluation.domain.index.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 增量索引触发事件：可由 Kafka 或 GitHub Webhook 等入口构造。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitEvent {

    /** 仓库 ID */
    private String repoId;

    /** 新提交的 commit hash（WebHook 的 {@code after}） */
    private String commitHash;

    /** 目标分支 */
    private String branch;

    /** 推送人或作者展示名 */
    private String pusher;

    /**
     * 用于 diff 的旧提交（WebHook 的 {@code before}）；可为 null。
     * GitHub 新建分支时 {@code before} 可能为 40 位全 0，编排器将改用「第一父提交」策略。
     */
    private String parentCommitHash;
}
