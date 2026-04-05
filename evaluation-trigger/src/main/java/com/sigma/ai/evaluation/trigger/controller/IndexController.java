package com.sigma.ai.evaluation.trigger.controller;

import com.sigma.ai.evaluation.api.dto.IndexTriggerRequest;
import com.sigma.ai.evaluation.api.dto.IndexTriggerResponse;
import com.sigma.ai.evaluation.domain.index.service.FullIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 索引触发 HTTP 入口，提供手动触发全量/增量索引的接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/index")
@RequiredArgsConstructor
public class IndexController {

    private final FullIndexService fullIndexService;

    /**
     * 手动触发全量索引。
     *
     * @param request 触发请求（repoId 必填，taskType 默认 FULL）
     * @return 任务信息
     */
    @PostMapping("/trigger")
    public ResponseEntity<IndexTriggerResponse> triggerIndex(@RequestBody IndexTriggerRequest request) {
        log.info("收到手动触发索引请求: repoId={}, taskType={}", request.getRepoId(), request.getTaskType());

        if (request.getRepoId() == null || request.getRepoId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    IndexTriggerResponse.builder().status("ERROR").message("repoId 不能为空").build());
        }

        // 异步执行，立即返回
        Thread.ofVirtual().start(() -> fullIndexService.runFullIndex(request.getRepoId()));

        return ResponseEntity.ok(IndexTriggerResponse.builder()
                .status("SUBMITTED")
                .message("全量索引任务已提交，后台异步执行")
                .build());
    }
}
