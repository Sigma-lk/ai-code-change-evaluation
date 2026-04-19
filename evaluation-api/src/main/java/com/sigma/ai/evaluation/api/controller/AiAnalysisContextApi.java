package com.sigma.ai.evaluation.api.controller;

import com.sigma.ai.evaluation.api.dto.AiAnalysisContextRequest;
import com.sigma.ai.evaluation.api.dto.AiAnalysisContextResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * AI 分析上下文组装 HTTP 契约：一次请求返回可嵌入提示词的结构化证据。
 */
@RequestMapping("/api/v1")
public interface AiAnalysisContextApi {

    /**
     * 组装分析上下文：子图多跳、语义命中、种子解析与截断说明。
     *
     * @param request 请求体（repoId 必填；commit、显式种子、semanticQueries 至少其一）
     * @return 结构化响应；参数不合法时由应用侧全局异常处理映射为 400
     */
    @PostMapping("/ai/analysis-context")
    ResponseEntity<AiAnalysisContextResponse> analysisContext(@RequestBody AiAnalysisContextRequest request);
}
