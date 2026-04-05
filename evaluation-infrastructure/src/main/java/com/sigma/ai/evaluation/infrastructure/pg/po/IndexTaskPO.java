package com.sigma.ai.evaluation.infrastructure.pg.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_index_task 表持久化对象。
 */
@Data
public class IndexTaskPO {

    private Long id;
    private String repoId;
    /** FULL / INCREMENTAL */
    private String taskType;
    private String triggerCommit;
    /** PENDING / RUNNING / SUCCESS / FAIL */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMsg;
}
