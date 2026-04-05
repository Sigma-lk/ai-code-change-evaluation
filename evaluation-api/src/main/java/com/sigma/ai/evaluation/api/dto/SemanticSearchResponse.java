package com.sigma.ai.evaluation.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 语义检索响应 DTO。
 */
@Data
@Builder
public class SemanticSearchResponse {

    /** 检索结果节点 ID 列表（按相似度降序） */
    private List<String> nodeIds;

    /** 检索耗时（毫秒） */
    private long elapsedMs;
}
