package com.sigma.ai.evaluation.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 影响面分析请求 DTO。
 */
@Data
public class ImpactAnalyzeRequest {

    /**
     * 变更的方法 ID 列表（格式：{ownerQualifiedName}#{methodName}({params})）。
     * 与 changedTypeNames 至少提供一个。
     */
    private List<String> changedMethodIds;

    /**
     * 变更的类型全限定名列表。
     * 与 changedMethodIds 至少提供一个。
     */
    private List<String> changedTypeNames;

    /**
     * 最大跳数，默认 3，最大 5。
     */
    private int maxHops = 3;
}
