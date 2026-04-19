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
    /** 0:PENDING 1:RUNNING 2:SUCCESS 3:FAIL */
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
