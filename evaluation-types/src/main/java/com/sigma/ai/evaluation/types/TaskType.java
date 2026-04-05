package com.sigma.ai.evaluation.types;

/**
 * 索引任务类型枚举。
 */
public enum TaskType {

    /** 全量索引：扫描仓库中所有 Java 文件重新构建图谱 */
    FULL,

    /** 增量索引：仅处理指定 Commit 变更的文件 */
    INCREMENTAL
}
