package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险传播 HTTP 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskPropagationRequest {

    /** 仓库 ID，与图中 JavaFile.repoId 对齐 */
    private String repoId;

    /**
     * 风险种子节点列表的 JSON 数组字符串，元素形如 {@code { "kind", "qualifiedName", "filePath" }}；
     * 由服务端反序列化为 {@link RiskNodeRefDto} 列表后再参与图查询。未传或空白视为空数组。
     */
    private String nodes;

    /** 最大传播深度；未传视为 30，非法值回退 30，大于 30 按 30 */
    private Integer propagationMaxDepth;

    private Integer maxNodes;

    private Integer maxEdges;
}
