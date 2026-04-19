package com.sigma.ai.evaluation.trigger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigma.ai.evaluation.api.controller.AiAnalysisContextApi;
import com.sigma.ai.evaluation.api.dto.AiAnalysisContextRequest;
import com.sigma.ai.evaluation.api.dto.AiAnalysisContextResponse;
import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyInput;
import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyOutput;
import com.sigma.ai.evaluation.domain.aicontext.AiContextAssemblyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向下游 AI 的单一组合接口：一次请求返回可嵌入提示词的结构化证据 JSON。
 *
 * <p>证据边界见 {@link AiAnalysisContextResponse} 类注释；未在 Neo4j 中建模的入口链路不在本接口承诺范围内。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AiContextController implements AiAnalysisContextApi {

    private final AiContextAssemblyService aiContextAssemblyService;
    private final ObjectMapper objectMapper;

    /**
     * 组装分析上下文：子图多跳、语义命中、种子解析与截断说明。
     *
     * @param request 请求体（repoId 必填；commit、显式种子、semanticQueries 至少其一）
     * @return 结构化响应；参数不合法时抛出 {@link com.sigma.ai.evaluation.types.exception.ParamValidationException} 由全局处理器映射为 400
     */
    @Override
    public ResponseEntity<AiAnalysisContextResponse> analysisContext(AiAnalysisContextRequest request) {
        log.info("AI 分析上下文请求: repoId={}, commitHash={}, semanticQueryCount={}, useSemanticHitsAsGraphSeeds={}",
                request.getRepoId(),
                request.getCommitHash(),
                request.getSemanticQueries() == null ? 0 : request.getSemanticQueries().size(),
                request.getUseSemanticHitsAsGraphSeeds());

        long start = System.currentTimeMillis();
        AiContextAssemblyInput input = objectMapper.convertValue(request, AiContextAssemblyInput.class);
        AiContextAssemblyOutput output = aiContextAssemblyService.assemble(input);
        AiAnalysisContextResponse response = objectMapper.convertValue(output, AiAnalysisContextResponse.class);

        log.info("AI 分析上下文响应: repoId={}, elapsedMs={}", request.getRepoId(), System.currentTimeMillis() - start);
        return ResponseEntity.ok(response);
    }
}
