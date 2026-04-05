package com.sigma.ai.evaluation.api.dto;

import lombok.Data;

/**
 * 手动触发索引请求 DTO。
 */
@Data
public class IndexTriggerRequest {

    /** 仓库 ID */
    private String repoId;

    /**
     * 任务类型：FULL（全量）/ INCREMENTAL（增量）。
     * 缺省为 FULL。
     */
    private String taskType = "FULL";

    /**
     * 增量任务时指定的 commit hash（INCREMENTAL 时必填）。
     */
    private String commitHash;
}
