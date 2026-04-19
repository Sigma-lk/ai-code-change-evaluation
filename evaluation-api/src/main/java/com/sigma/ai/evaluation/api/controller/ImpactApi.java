package com.sigma.ai.evaluation.api.controller;

import com.sigma.ai.evaluation.api.dto.ImpactAnalyzeRequest;
import com.sigma.ai.evaluation.api.dto.ImpactAnalyzeResponse;
import com.sigma.ai.evaluation.api.dto.SemanticSearchRequest;
import com.sigma.ai.evaluation.api.dto.SemanticSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 影响面分析与语义检索 HTTP 契约。
 */
@RequestMapping("/api/v1")
public interface ImpactApi {

    /**
     * 影响面分析：给定变更方法/类型，返回受影响的调用链与子类型。
     *
     * @param request 分析请求
     * @return 影响节点集合
     */
    @PostMapping("/impact/analyze")
    ResponseEntity<ImpactAnalyzeResponse> analyze(@RequestBody ImpactAnalyzeRequest request);

    /**
     * 语义检索：基于向量相似度检索最相关的代码节点。
     *
     * @param request 检索请求
     * @return 节点 ID 列表
     */
    @PostMapping("/search/semantic")
    ResponseEntity<SemanticSearchResponse> semanticSearch(@RequestBody SemanticSearchRequest request);
}
