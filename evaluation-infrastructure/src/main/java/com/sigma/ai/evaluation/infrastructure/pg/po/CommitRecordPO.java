package com.sigma.ai.evaluation.infrastructure.pg.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_commit_record 表持久化对象，记录已处理的提交，用于增量消费幂等控制。
 */
@Data
public class CommitRecordPO {

    private Long id;
    private String repoId;
    private String commitHash;
    private String author;
    private LocalDateTime commitTime;
    private Integer changedFileCount;
    /** 1:PROCESSED 2:SKIPPED */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
