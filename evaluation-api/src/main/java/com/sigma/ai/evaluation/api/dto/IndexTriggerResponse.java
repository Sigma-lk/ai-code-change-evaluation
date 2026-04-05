package com.sigma.ai.evaluation.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 手动触发索引响应 DTO。
 */
@Data
@Builder
public class IndexTriggerResponse {

    /** 创建的任务 ID */
    private Long taskId;

    /** 任务状态（初始为 RUNNING） */
    private String status;

    /** 提示信息 */
    private String message;
}
