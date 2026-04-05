package com.sigma.ai.evaluation.trigger.consumer;

import lombok.Data;

/**
 * Git post-receive Hook 通过 Kafka 投递的提交事件消息体。
 */
@Data
public class CommitEvent {

    /** 仓库 ID */
    private String repoId;

    /** 新提交的 commit hash */
    private String commitHash;

    /** 目标分支 */
    private String branch;

    /** 推送人 */
    private String pusher;

    /** 父提交 hash（用于 diff，可为 null） */
    private String parentCommitHash;
}
