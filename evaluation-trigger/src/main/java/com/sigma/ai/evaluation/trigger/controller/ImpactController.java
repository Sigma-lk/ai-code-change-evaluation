package com.sigma.ai.evaluation.trigger.controller;

import com.sigma.ai.evaluation.api.controller.ImpactApi;
import com.sigma.ai.evaluation.api.dto.ImpactAnalyzeRequest;
import com.sigma.ai.evaluation.api.dto.ImpactAnalyzeResponse;
import com.sigma.ai.evaluation.api.dto.SemanticSearchRequest;
import com.sigma.ai.evaluation.api.dto.SemanticSearchResponse;
import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.domain.impact.model.ImpactQuery;
import com.sigma.ai.evaluation.domain.impact.model.ImpactResult;
import com.sigma.ai.evaluation.domain.impact.service.ImpactAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 影响面分析与语义检索 HTTP 入口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ImpactController implements ImpactApi {

    private final ImpactAnalysisService impactAnalysisService;
    private final EmbeddingStoreAdapter embeddingStoreAdapter;

    /**
     * 影响面分析：给定变更方法/类型，返回受影响的调用链与子类型。
     *
     * @param request 分析请求
     * @return 影响节点集合
     */
    @Override
    public ResponseEntity<ImpactAnalyzeResponse> analyze(ImpactAnalyzeRequest request) {
        log.info("影响面分析请求: changedMethods={}, changedTypes={}, maxHops={}",
                request.getChangedMethodIds() != null ? request.getChangedMethodIds().size() : 0,
                request.getChangedTypeNames() != null ? request.getChangedTypeNames().size() : 0,
                request.getMaxHops());

        ImpactQuery query = ImpactQuery.builder()
                .changedMethodIds(request.getChangedMethodIds())
                .changedTypeNames(request.getChangedTypeNames())
                .maxHops(Math.min(request.getMaxHops(), 5))
                .build();

        ImpactResult result = impactAnalysisService.analyze(query);

        return ResponseEntity.ok(ImpactAnalyzeResponse.builder()
                .impactedMethodIds(result.getImpactedMethodIds())
                .impactedSubTypeNames(result.getImpactedSubTypeNames())
                .maxHops(result.getMaxHops())
                .build());
    }

    /**
     * 语义检索：基于向量相似度检索最相关的代码节点。
     *
     * @param request 检索请求
     * @return 节点 ID 列表
     */
    @Override
    public ResponseEntity<SemanticSearchResponse> semanticSearch(SemanticSearchRequest request) {
        log.info("语义检索请求: query={}, repoId={}, topK={}", request.getQuery(), request.getRepoId(), request.getTopK());
        long start = System.currentTimeMillis();

        List<String> nodeIds = embeddingStoreAdapter.semanticSearch(
                request.getQuery(), request.getRepoId(), request.getTopK());

        return ResponseEntity.ok(SemanticSearchResponse.builder()
                .nodeIds(nodeIds)
                .elapsedMs(System.currentTimeMillis() - start)
                .build());
    }
}
