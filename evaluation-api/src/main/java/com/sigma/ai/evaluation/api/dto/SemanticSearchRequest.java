package com.sigma.ai.evaluation.api.dto;

import lombok.Data;

/**
 * 语义检索请求 DTO。
 */
@Data
public class SemanticSearchRequest {

    /** 自然语言查询文本 */
    private String query;

    /** 仓库 ID 过滤（null 表示全库检索） */
    private String repoId;

    /** 返回结果数量，默认 10 */
    private int topK = 10;
}
