package com.sigma.ai.evaluation.domain.repository.adapter;

/**
 * 已处理提交记录持久化 Port，操作 t_commit_record 表，用于增量消费幂等控制。
 * 由 evaluation-infrastructure 模块实现。
 */
public interface CommitRecordPort {

    /**
     * 检查指定仓库的指定提交是否已被处理。
     *
     * @param repoId     仓库 ID
     * @param commitHash commit hash
     * @return true 表示已处理，应跳过
     */
    boolean isProcessed(String repoId, String commitHash);

    /**
     * 标记提交已处理完毕。
     *
     * @param repoId           仓库 ID
     * @param commitHash       commit hash
     * @param author           提交作者
     * @param commitTimeMillis 提交时间（epoch millis）
     * @param changedFileCount 本次变更文件数
     */
    void markProcessed(String repoId, String commitHash, String author,
                       long commitTimeMillis, int changedFileCount);
}
