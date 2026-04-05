package com.sigma.ai.evaluation.infrastructure.pg.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_parse_error 表持久化对象，记录文件解析失败详情。
 */
@Data
public class ParseErrorPO {

    private Long id;
    private Long taskId;
    private String filePath;
    private String errorType;
    private String errorMsg;
    private LocalDateTime createdAt;
}
