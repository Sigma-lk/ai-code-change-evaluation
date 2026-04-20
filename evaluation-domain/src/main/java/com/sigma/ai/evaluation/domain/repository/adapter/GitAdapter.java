package com.sigma.ai.evaluation.domain.repository.adapter;

import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;
import com.sigma.ai.evaluation.domain.repository.model.DiffLineStats;

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
     * 从默认 remote（通常为 origin）抓取引用与对象，保证本地可解析 Webhook 中的 {@code before}/{@code after}。
     *
     * @param localPath 本地 Git 仓库根目录
     */
    void fetch(String localPath);

    /**
     * 统计两次提交之间 Java 文件的增删行数（基于文本 diff，非语法树级）。
     *
     * @param localPath 本地仓库路径
     * @param oldCommit   旧提交（tree 作为 diff 左侧）
     * @param newCommit   新提交（tree 作为 diff 右侧）
     * @return 汇总统计；无法解析提交或发生错误时返回全 0（并打日志）
     */
    DiffLineStats diffLineStats(String localPath, String oldCommit, String newCommit);

    /**
     * 生成单个 Java 文件在两次提交之间的 unified diff 文本（含 diff --git / --- / +++ / @@ 等）。
     *
     * @param localPath    本地仓库根路径
     * @param oldCommit    旧提交（可带 {@code ^} 等 rev 语法）
     * @param newCommit    新提交
     * @param relativePath 仓库相对路径，使用 {@code /} 分隔（与 JGit DiffEntry 一致）
     * @return diff 文本；无对应变更或无法生成时返回空串
     */
    String unifiedDiffForJavaFile(String localPath, String oldCommit, String newCommit, String relativePath);

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
     * 将指定提交与其第一父提交做 tree diff，得到变更的 Java 文件列表（行为与 {@link #diffCommits} 一致）。
     * 用于根提交等无父场景：父无法解析时返回空列表。
     *
     * @param localPath   本地仓库路径
     * @param commitHash  目标提交
     */
    List<ChangedFile> diffCommitAgainstFirstParent(String localPath, String commitHash);

    /**
     * 获取指定分支最新 HEAD 的提交 hash。
     *
     * @param localPath 本地仓库路径
     * @param branch    分支名
     * @return 最新 commit hash
     */
    String getHeadCommitHash(String localPath, String branch);
}
