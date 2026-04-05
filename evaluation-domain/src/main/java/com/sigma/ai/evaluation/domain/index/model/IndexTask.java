package com.sigma.ai.evaluation.domain.index.model;

import com.sigma.ai.evaluation.types.TaskStatus;
import com.sigma.ai.evaluation.types.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 索引任务领域对象，对应 t_index_task 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexTask {

    private Long id;

    /** 关联仓库 ID */
    private String repoId;

    /** 任务类型：FULL / INCREMENTAL */
    private TaskType taskType;

    /** 触发本次任务的 commit hash（增量任务使用） */
    private String triggerCommit;

    /** 任务状态 */
    private TaskStatus status;

    /** 任务开始时间 */
    private Instant startedAt;

    /** 任务结束时间 */
    private Instant finishedAt;

    /** 失败时的错误信息 */
    private String errorMsg;
}
