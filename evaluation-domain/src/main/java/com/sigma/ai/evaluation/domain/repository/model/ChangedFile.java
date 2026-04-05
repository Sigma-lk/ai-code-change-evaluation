package com.sigma.ai.evaluation.domain.repository.model;

import com.sigma.ai.evaluation.types.FileChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示 Git Diff 中一个发生变更的文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangedFile {

    /** 文件相对于仓库根目录的路径 */
    private String relativePath;

    /** 文件绝对路径 */
    private String absolutePath;

    /** 变更类型：ADDED / MODIFIED / DELETED */
    private FileChangeType changeType;
}
