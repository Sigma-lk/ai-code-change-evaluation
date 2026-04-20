package com.sigma.ai.evaluation.domain.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 两次提交之间 Java 源码行的增删统计（由 JGit 基于文本 diff 汇总）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffLineStats {

    /** 参与统计的变更 Java 文件数（与 diff 条目一致，不含非 java） */
    private int javaFilesTouched;

    /** 新增行数（近似：新文本侧编辑区长度之和） */
    private int totalInsertions;

    /** 删除行数（近似：旧文本侧编辑区长度之和） */
    private int totalDeletions;
}
