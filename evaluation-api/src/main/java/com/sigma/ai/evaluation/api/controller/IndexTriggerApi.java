package com.sigma.ai.evaluation.api.controller;

import com.sigma.ai.evaluation.api.dto.IndexTriggerRequest;
import com.sigma.ai.evaluation.api.dto.IndexTriggerResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 索引触发 HTTP 契约：手动触发全量/增量索引等入口。
 */
@RequestMapping("/api/v1/index")
public interface IndexTriggerApi {

    /**
     * 手动触发全量索引。
     *
     * @param request 触发请求（repoId 必填，taskType 默认 FULL）
     * @return 任务信息
     */
    @PostMapping("/trigger")
    ResponseEntity<IndexTriggerResponse> triggerIndex(@RequestBody IndexTriggerRequest request);
}
