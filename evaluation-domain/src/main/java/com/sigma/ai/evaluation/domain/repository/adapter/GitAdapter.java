package com.sigma.ai.evaluation.domain.repository.adapter;

import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;

import java.util.List;

/**
 * Git 操作 Adapter，封装 JGit 的 clone/pull/diff 能力。
 * 由 evaluation-infrastructure 模块实现。
 */
public interface GitAdapter {

    /**
     * 克隆远程仓库到本地目录。
     * 若目录已存在且是有效 Git 仓库，则执行 pull 代替 clone。
     *
     * @param cloneUrl  远程仓库 URL
     * @param branch    目标分支
     * @param localPath 本地存储路径
     */
    void cloneOrPull(String cloneUrl, String branch, String localPath);

    /**
     * 获取两次提交之间的变更文件列表。
     *
     * @param localPath  本地仓库路径
     * @param oldCommit  旧提交 hash（可为 null，表示与 HEAD~1 比较）
     * @param newCommit  新提交 hash
     * @return 变更文件列表（含 changeType）
     */
    List<ChangedFile> diffCommits(String localPath, String oldCommit, String newCommit);

    /**
     * 获取指定分支最新 HEAD 的提交 hash。
     *
     * @param localPath 本地仓库路径
     * @param branch    分支名
     * @return 最新 commit hash
     */
    String getHeadCommitHash(String localPath, String branch);
}
