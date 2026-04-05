package com.sigma.ai.evaluation.infrastructure.pg.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_repository 表持久化对象。
 */
@Data
public class RepositoryPO {

    private String id;
    private String name;
    private String cloneUrl;
    private String branch;
    private String localPath;
    /** ACTIVE / INACTIVE */
    private String status;
    private LocalDateTime createdAt;
}
