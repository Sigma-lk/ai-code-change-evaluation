package com.sigma.ai.evaluation.trigger.controller;

import com.sigma.ai.evaluation.api.controller.IndexTriggerApi;
import com.sigma.ai.evaluation.api.dto.IndexTriggerRequest;
import com.sigma.ai.evaluation.api.dto.IndexTriggerResponse;
import com.sigma.ai.evaluation.domain.index.service.FullIndexService;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Executor;

/**
 * 索引触发 HTTP 入口，提供手动触发全量/增量索引的接口。
 */
@Slf4j
@RestController
public class IndexController implements IndexTriggerApi {

    private final FullIndexService fullIndexService;
    private final Executor indexTaskExecutor;

    public IndexController(FullIndexService fullIndexService,
                           @Qualifier("indexTaskExecutor") Executor indexTaskExecutor) {
        this.fullIndexService = fullIndexService;
        this.indexTaskExecutor = indexTaskExecutor;
    }

    /**
     * 手动触发全量索引。
     *
     * @param request 触发请求（repoId 必填，taskType 默认 FULL）
     * @return 任务信息
     */
    @Override
    public ResponseEntity<IndexTriggerResponse> triggerIndex(IndexTriggerRequest request) {
        log.info("收到手动触发索引请求: repoId={}, taskType={}", request.getRepoId(), request.getTaskType());

        if (request.getRepoId() == null || request.getRepoId().isBlank()) {
            throw ParamValidationException.repoIdEmpty();
        }

        // 异步提交至线程池，立即返回
        indexTaskExecutor.execute(() -> fullIndexService.runFullIndex(request.getRepoId()));

        return ResponseEntity.ok(IndexTriggerResponse.builder()
                .status("SUBMITTED")
                .message("全量索引任务已提交，后台异步执行")
                .build());
    }
}
