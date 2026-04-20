package com.sigma.ai.evaluation.api.controller;

import com.sigma.ai.evaluation.api.dto.RiskPropagationRequest;
import com.sigma.ai.evaluation.api.dto.RiskPropagationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 代码风险在知识图谱上的传播链查询 HTTP 契约。
 */
@RequestMapping("/api/v1")
public interface RiskPropagationApi {

    /**
     * 根据传入的 nodes 种子列表，返回每个种子对应的风险传播链。
     *
     * @param request 仓库 ID、{@code nodes}（JSON 数组字符串）、可选传播深度与预算
     * @return 与 nodes 顺序对齐的传播结果
     */
    @PostMapping("/risk/propagation")
    ResponseEntity<RiskPropagationResponse> propagate(@RequestBody RiskPropagationRequest request);
}
