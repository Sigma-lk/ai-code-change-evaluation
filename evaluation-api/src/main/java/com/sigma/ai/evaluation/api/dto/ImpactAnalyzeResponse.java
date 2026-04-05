package com.sigma.ai.evaluation.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 影响面分析响应 DTO。
 */
@Data
@Builder
public class ImpactAnalyzeResponse {

    /** 受影响的方法 ID 列表 */
    private List<String> impactedMethodIds;

    /** 受影响的子类型全限定名列表 */
    private List<String> impactedSubTypeNames;

    /** 本次分析使用的最大跳数 */
    private int maxHops;
}
